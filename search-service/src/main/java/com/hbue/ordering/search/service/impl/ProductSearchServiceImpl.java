package com.hbue.ordering.search.service.impl;

import com.hbue.ordering.search.service.ProductSearchService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.product.api.searchclient.ProductSearchSourceClient;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.search.document.MenuProductDocument;
import com.hbue.ordering.search.support.SearchIndexLock;
import com.hbue.ordering.search.support.SearchIndexManager;
import com.hbue.ordering.search.vo.ProductSearchPageVO;
import com.hbue.ordering.search.vo.ProductSearchResultVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 商品搜索查询、窗口索引刷新与全量重建。 */
@Service
public class ProductSearchServiceImpl implements ProductSearchService {
    private final ElasticsearchOperations elasticsearch;
    private final ProductSearchSourceClient productSource;
    private final SearchIndexLock indexLock;
    private final SearchIndexManager indexManager;
    private final ObjectMapper json;
    private final String internalToken;

    public ProductSearchServiceImpl(ElasticsearchOperations elasticsearch, ProductSearchSourceClient productSource,
                                SearchIndexLock indexLock, SearchIndexManager indexManager, ObjectMapper json,
                                @Value("${internal.scope.token:}") String internalToken) {
        this.elasticsearch = elasticsearch;
        this.productSource = productSource;
        this.indexLock = indexLock;
        this.indexManager = indexManager;
        this.json = json;
        this.internalToken = internalToken;
    }

    public ProductSearchPageVO search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size) {
        String q;
        try { q = json.writeValueAsString(keyword.trim()); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("搜索词无法编码", ex); }
        StringBuilder query = new StringBuilder("{\"bool\":{\"must\":[{\"multi_match\":{\"query\":")
                .append(q).append(",\"type\":\"bool_prefix\",\"fields\":[\"productName\",\"productName._2gram\",\"productName._3gram\",\"description\",\"categoryName\",\"windowName\",\"restaurantName\"]}}],\"filter\":[");
        List<String> filters = new ArrayList<>();
        if (restaurantId != null) filters.add("{\"term\":{\"restaurantId\":" + restaurantId + "}}");
        if (windowId != null) filters.add("{\"term\":{\"windowId\":" + windowId + "}}");
        if (categoryId != null) filters.add("{\"term\":{\"categoryId\":" + categoryId + "}}");
        query.append(String.join(",", filters)).append("]}} ");
        StringQuery request = new StringQuery(query.toString());
        request.setPageable(PageRequest.of(page - 1, size));
        SearchHits<MenuProductDocument> hits = elasticsearch.search(request, MenuProductDocument.class);
        List<ProductSearchResultVO> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent).map(this::toResult).toList();
        return new ProductSearchPageVO(keyword.trim(), page, size, hits.getTotalHits(), results);
    }

    /** 当前窗口按主库快照整体替换，事件重复消费会得到相同结果。 */
    public void refreshWindow(Long windowId) {
        if (windowId == null || windowId <= 0) throw new IllegalArgumentException("窗口 ID 无效");
        indexLock.withReadLock(() -> indexLock.withWindowLock(windowId, () -> {
            ApiResponse<List<ProductSearchDocument>> response = productSource.loadWindowDocuments(internalToken, windowId);
            List<ProductSearchDocument> snapshot = requireData(response, "商品服务未返回窗口数据");
            Query deleteWindow = new StringQuery("{\"term\":{\"windowId\":" + windowId + "}}");
            elasticsearch.delete(DeleteQuery.builder(deleteWindow).build(), MenuProductDocument.class);
            List<MenuProductDocument> documents = snapshot.stream().map(this::toDocument).toList();
            documents.forEach(elasticsearch::save);
            return null;
        }));
    }

    @Override
    public void refreshProduct(Long windowId, Long productId) {
        if (windowId == null || windowId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("窗口或菜品 ID 无效");
        }
        indexLock.withReadLock(() -> indexLock.withWindowLock(windowId, () -> {
            List<ProductSearchDocument> snapshot = requireData(
                    productSource.loadProductDocument(internalToken, windowId, productId), "商品服务未返回菜品数据");
            if (snapshot.isEmpty()) {
                elasticsearch.delete(windowId + ":" + productId, MenuProductDocument.class);
            } else if (snapshot.size() == 1 && productId.equals(snapshot.getFirst().productId())
                    && windowId.equals(snapshot.getFirst().windowId())) {
                elasticsearch.save(toDocument(snapshot.getFirst()));
            } else {
                throw new IllegalStateException("商品服务返回了不匹配的菜品快照");
            }
            return null;
        }));
    }

    /** 在影子索引写入完整快照，校验后切换别名。 */
    public int rebuildAll() {
        return indexLock.withWriteLock(() -> {
            List<Long> windowIds = requireData(productSource.listSearchableWindowIds(internalToken), "商品服务未返回窗口列表");
            String newIndex = "menu-products-" + UUID.randomUUID().toString().replace("-", "");
            indexManager.createIndex(newIndex);
            IndexCoordinates target = IndexCoordinates.of(newIndex);
            long expected = 0;
            for (Long windowId : windowIds) {
                List<ProductSearchDocument> snapshot = requireData(
                        productSource.loadWindowDocuments(internalToken, windowId), "商品服务未返回窗口数据");
                List<MenuProductDocument> documents = snapshot.stream().map(this::toDocument).toList();
                if (!documents.isEmpty()) elasticsearch.save(documents, target);
                expected += documents.size();
            }
            elasticsearch.indexOps(target).refresh();
            long actual = elasticsearch.count(Query.findAll(), MenuProductDocument.class, target);
            if (expected != actual) throw new IllegalStateException("搜索索引重建数量不一致");
            // 切换期间写入事件等待读锁；切换后它们重新读取商品最新状态。
            indexManager.switchAlias(newIndex);
            return windowIds.size();
        });
    }

    @Override
    public int synchronizeExistingWindows() {
        if (elasticsearch.count(Query.findAll(), MenuProductDocument.class) > 0) return 0;
        return rebuildAll();
    }

    @Override
    public int reconcile() {
        return indexLock.withReadLock(() -> {
            List<Long> windowIds = requireData(productSource.listSearchableWindowIds(internalToken), "商品服务未返回窗口列表");
            Map<String, ProductSearchDocument> expected = new HashMap<>();
            for (Long windowId : windowIds) {
                for (ProductSearchDocument document : requireData(
                        productSource.loadWindowDocuments(internalToken, windowId), "商品服务未返回窗口数据")) {
                    String id = document.windowId() + ":" + document.productId();
                    if (expected.put(id, document) != null) throw new IllegalStateException("商品快照存在重复菜品：" + id);
                }
            }
            Set<String> found = new HashSet<>();
            Map<String, long[]> repairs = new HashMap<>();
            Query all = Query.findAll();
            all.setPageable(PageRequest.of(0, 500));
            try (SearchHitsIterator<MenuProductDocument> hits = elasticsearch.searchForStream(all, MenuProductDocument.class)) {
                while (hits.hasNext()) {
                    MenuProductDocument actual = hits.next().getContent();
                    if (actual.getWindowId() == null || actual.getProductId() == null) {
                        throw new IllegalStateException("搜索索引存在缺少菜品或窗口 ID 的文档");
                    }
                    String id = actual.getWindowId() + ":" + actual.getProductId();
                    found.add(id);
                    ProductSearchDocument wanted = expected.get(id);
                    if (wanted == null || !sameDocument(actual, toDocument(wanted))) {
                        repairs.put(id, new long[]{actual.getWindowId(), actual.getProductId()});
                    }
                }
            }
            for (Map.Entry<String, ProductSearchDocument> entry : expected.entrySet()) {
                if (!found.contains(entry.getKey())) {
                    ProductSearchDocument document = entry.getValue();
                    repairs.put(entry.getKey(), new long[]{document.windowId(), document.productId()});
                }
            }
            // 快照可能已过期，修复时再次读取商品当前状态，避免覆盖并发变更。
            for (long[] repair : repairs.values()) refreshProduct(repair[0], repair[1]);
            return repairs.size();
        });
    }

    private boolean sameDocument(MenuProductDocument left, MenuProductDocument right) {
        return Objects.equals(left.getProductId(), right.getProductId())
                && Objects.equals(left.getWindowId(), right.getWindowId())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getRestaurantId(), right.getRestaurantId())
                && Objects.equals(left.getRestaurantName(), right.getRestaurantName())
                && Objects.equals(left.getRestaurantLocation(), right.getRestaurantLocation())
                && Objects.equals(left.getWindowName(), right.getWindowName())
                && Objects.equals(left.getCategoryName(), right.getCategoryName())
                && Objects.equals(left.getProductName(), right.getProductName())
                && Objects.equals(left.getDescription(), right.getDescription())
                && Objects.equals(left.getImageUrl(), right.getImageUrl())
                && Objects.equals(left.getSortOrder(), right.getSortOrder())
                && (left.getPrice() == null ? right.getPrice() == null
                    : right.getPrice() != null && left.getPrice().compareTo(right.getPrice()) == 0);
    }

    private MenuProductDocument toDocument(ProductSearchDocument source) {
        MenuProductDocument d = new MenuProductDocument();
        d.setId(source.windowId() + ":" + source.productId());
        d.setProductId(source.productId()); d.setCategoryId(source.categoryId()); d.setWindowId(source.windowId()); d.setRestaurantId(source.restaurantId());
        d.setRestaurantName(source.restaurantName()); d.setRestaurantLocation(source.restaurantLocation());
        d.setWindowName(source.windowName()); d.setCategoryName(source.categoryName()); d.setProductName(source.productName());
        d.setDescription(source.description()); d.setImageUrl(source.imageUrl()); d.setPrice(source.price()); d.setSortOrder(source.sortOrder());
        return d;
    }

    private ProductSearchResultVO toResult(MenuProductDocument document) {
        return new ProductSearchResultVO(document.getProductId(), document.getWindowId(), document.getRestaurantId(),
                document.getRestaurantName(), document.getRestaurantLocation(), document.getWindowName(),
                document.getCategoryName(), document.getProductName(), document.getDescription(),
                document.getImageUrl(), document.getPrice());
    }

    private <T> T requireData(ApiResponse<T> response, String message) {
        if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException(message);
        }
        return response.getData();
    }
}
