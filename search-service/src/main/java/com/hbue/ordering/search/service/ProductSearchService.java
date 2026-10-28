package com.hbue.ordering.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.common.redis.DistributedLockService;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.product.api.searchclient.ProductSearchSourceClient;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.search.model.MenuProductDocument;
import com.hbue.ordering.search.model.ProductSearchPage;
import com.hbue.ordering.search.model.ProductSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 商品搜索查询、窗口索引刷新与全量重建。 */
@Service
public class ProductSearchService {
    private static final String LOCK_PREFIX = "search:menu:reindex:";
    private final ElasticsearchOperations elasticsearch;
    private final ProductSearchSourceClient productSource;
    private final DistributedLockService locks;
    private final ObjectMapper json;
    private final String internalToken;

    public ProductSearchService(ElasticsearchOperations elasticsearch, ProductSearchSourceClient productSource,
                                DistributedLockService locks, ObjectMapper json,
                                @Value("${internal.scope.token:}") String internalToken) {
        this.elasticsearch = elasticsearch;
        this.productSource = productSource;
        this.locks = locks;
        this.json = json;
        this.internalToken = internalToken;
    }

    public ProductSearchPage search(String keyword, Long restaurantId, Long windowId, Long categoryId, int page, int size) {
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
        List<ProductSearchResult> results = hits.getSearchHits().stream()
                .map(SearchHit::getContent).map(ProductSearchResult::from).toList();
        return new ProductSearchPage(keyword.trim(), page, size, hits.getTotalHits(), results);
    }

    /** 当前窗口按主库快照整体替换，事件重复消费会得到相同结果。 */
    public void refreshWindow(Long windowId) {
        if (windowId == null || windowId <= 0) throw new IllegalArgumentException("窗口 ID 无效");
        try (DistributedLockService.LockHandle lock = locks.tryAcquire(LOCK_PREFIX + windowId, 3, 600)) {
            if (lock == null) throw new IllegalStateException("窗口索引正在重建，稍后重试");
            ApiResponse<List<ProductSearchDocument>> response = productSource.loadWindowDocuments(internalToken, windowId);
            List<ProductSearchDocument> snapshot = requireData(response, "商品服务未返回窗口数据");
            Query deleteWindow = new StringQuery("{\"term\":{\"windowId\":" + windowId + "}}");
            elasticsearch.delete(deleteWindow, MenuProductDocument.class);
            List<MenuProductDocument> documents = snapshot.stream().map(this::toDocument).toList();
            documents.forEach(elasticsearch::save);
        }
    }

    /** 管理用途的全量重建；按窗口逐个加锁刷新，MySQL 保持唯一事实来源。 */
    public int rebuildAll() {
        ApiResponse<List<Long>> response = productSource.listSearchableWindowIds(internalToken);
        List<Long> windowIds = requireData(response, "商品服务未返回窗口列表");
        elasticsearch.delete(new StringQuery("{\"match_all\":{}}"), MenuProductDocument.class);
        for (Long windowId : windowIds) refreshWindow(windowId);
        return windowIds.size();
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

    private <T> T requireData(ApiResponse<T> response, String message) {
        if (response == null || response.getData() == null) throw new IllegalStateException(message);
        return response.getData();
    }
}
