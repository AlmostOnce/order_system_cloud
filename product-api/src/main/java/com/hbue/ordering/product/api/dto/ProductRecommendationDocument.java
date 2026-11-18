package com.hbue.ordering.product.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务提供给推荐服务的当前目录快照。
 *
 * <p>快照只包含当前有效商品展示所需的商品信息和可选规格文字；规格选项表示
 * “商品当前可以提供什么”，不能单独作为某位顾客选过或偏好的证据。</p>
 */
public record ProductRecommendationDocument(
        Long productId,
        Long categoryId,
        Long windowId,
        Long restaurantId,
        String windowName,
        String categoryName,
        String productName,
        String description,
        String imageUrl,
        BigDecimal basePrice,
        Integer sortOrder,
        boolean hasSpecifications,
        List<SpecificationGroup> specificationGroups
) {
    /**
     * 当前启用的规格组及其选项；只供商品级口味候选匹配，不用于核价或库存拆分。
     */
    public record SpecificationGroup(Long groupId, String groupName, List<SpecificationOption> options) {
    }

    /**
     * 当前启用的规格选项文字；价格和必选规则继续由商品核价接口权威处理。
     */
    public record SpecificationOption(Long optionId, String optionName) {
    }
}
