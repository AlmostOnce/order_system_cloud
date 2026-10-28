package com.hbue.ordering.product.api.dto;

import java.math.BigDecimal;

/** 商品服务提供给搜索服务的当前可检索菜品快照。 */
public record ProductSearchDocument(
        Long productId,
        Long categoryId,
        Long windowId,
        Long restaurantId,
        String restaurantName,
        String restaurantLocation,
        String windowName,
        String categoryName,
        String productName,
        String description,
        String imageUrl,
        BigDecimal price,
        Integer sortOrder
) {}
