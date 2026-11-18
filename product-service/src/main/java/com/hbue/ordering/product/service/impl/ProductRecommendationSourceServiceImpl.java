package com.hbue.ordering.product.service.impl;

import com.hbue.ordering.product.api.dto.ProductRecommendationDocument;
import com.hbue.ordering.product.api.dto.ProductRecommendationValidationRequest;
import com.hbue.ordering.product.api.dto.ProductSearchDocument;
import com.hbue.ordering.product.service.ProductRecommendationSourceService;
import com.hbue.ordering.product.service.ProductSearchSourceService;
import com.hbue.ordering.product.service.ProductSpecificationService;
import com.hbue.ordering.product.vo.ProductSpecificationGroupVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 在商品域当前有效性规则下批量读取推荐候选及其可选规格说明。
 */
@Service
public class ProductRecommendationSourceServiceImpl implements ProductRecommendationSourceService {
    private static final int ACTIVE = 1;

    private final ProductSearchSourceService searchableWindows;
    private final ProductSpecificationService specifications;

    /**
     * 注入可检索窗口、商品、分类和规格读取能力，目录快照不复制交易规则。
     */
    public ProductRecommendationSourceServiceImpl(ProductSearchSourceService searchableWindows,
                                                  ProductSpecificationService specifications) {
        this.searchableWindows = searchableWindows;
        this.specifications = specifications;
    }

    /**
     * 复用商品搜索域已核验的启用餐厅和窗口列表，保持目录筛选规则一致。
     */
    @Override
    public List<Long> windowIds() {
        return searchableWindows.searchableWindowIds();
    }

    /**
     * 窗口和商品必须仍有效；规格组通过一次批量查询附加，不随菜品数量增加 SQL 次数。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProductRecommendationDocument> documents(Long windowId) {
        if (windowId == null || windowId <= 0) return List.of();
        // 复用商品搜索域一次读取的有效商品快照，再对整批商品一次加载规格，避免逐菜数据库查询。
        List<ProductSearchDocument> available = searchableWindows.documents(windowId);
        if (available.isEmpty()) return List.of();
        Map<Long, List<ProductSpecificationGroupVO>> specByProduct = specifications.loadActive(
                available.stream().map(ProductSearchDocument::productId).toList());
        return available.stream().map(document -> toDocument(document,
                specByProduct.getOrDefault(document.productId(), List.of()))).toList();
    }

    /**
     * 单菜刷新仍复用同窗口的同一套状态、分类及规格校验，不暴露已失效商品。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProductRecommendationDocument> document(Long windowId, Long productId) {
        if (productId == null || productId <= 0) return List.of();
        return documents(windowId).stream().filter(item -> productId.equals(item.productId())).toList();
    }

    /**
     * 对不超过50个候选按窗口批量读取当前商品状态；逐窗读取避免N+1查询，返回顺序与请求一致。
     * 客户端传入的展示字段和价格不会进入本方法。
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProductRecommendationDocument> validateCandidates(ProductRecommendationValidationRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty() || request.items().size() > 50) {
            throw new IllegalArgumentException("待复核推荐菜品必须为1至50项");
        }
        Set<Long> productIds = new HashSet<>();
        Map<Long, List<ProductRecommendationValidationRequest.Item>> byWindow = new HashMap<>();
        for (ProductRecommendationValidationRequest.Item item : request.items()) {
            if (item == null || item.productId() == null || item.productId() <= 0
                    || item.windowId() == null || item.windowId() <= 0 || !productIds.add(item.productId())) {
                throw new IllegalArgumentException("待复核菜品编号无效或重复");
            }
            byWindow.computeIfAbsent(item.windowId(), ignored -> new java.util.ArrayList<>()).add(item);
        }
        Map<Long, ProductRecommendationDocument> current = new HashMap<>();
        for (var window : byWindow.entrySet()) {
            Set<Long> requestedIds = window.getValue().stream()
                    .map(ProductRecommendationValidationRequest.Item::productId).collect(Collectors.toSet());
            for (ProductRecommendationDocument document : documents(window.getKey())) {
                if (requestedIds.contains(document.productId())) current.put(document.productId(), document);
            }
        }
        return request.items().stream().map(item -> current.get(item.productId())).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * 将商品域视图转换为只读推荐快照；库存和最终核价仍由交易链路单独确认。
     */
    private ProductRecommendationDocument toDocument(ProductSearchDocument product,
                                                     List<ProductSpecificationGroupVO> groups) {
        List<ProductRecommendationDocument.SpecificationGroup> groupSnapshots = groups.stream()
                .map(group -> new ProductRecommendationDocument.SpecificationGroup(group.getGroupId(), group.getName(),
                        group.getOptions().stream().map(option -> new ProductRecommendationDocument.SpecificationOption(
                                option.getOptionId(), option.getName())).toList()))
                .toList();
        return new ProductRecommendationDocument(product.productId(), product.categoryId(), product.windowId(),
                product.restaurantId(), product.windowName(), product.categoryName(), product.productName(), product.description(),
                product.imageUrl(), product.price(), product.sortOrder(),
                !groupSnapshots.isEmpty(), groupSnapshots);
    }
}
