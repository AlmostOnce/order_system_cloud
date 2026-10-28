package com.hbue.ordering.search.model;

import java.math.BigDecimal;

public record ProductSearchResult(Long productId, Long windowId, Long restaurantId, String restaurantName,
                                  String restaurantLocation, String windowName, String categoryName,
                                  String productName, String description, String imageUrl, BigDecimal price) {
    public static ProductSearchResult from(MenuProductDocument d) {
        return new ProductSearchResult(d.getProductId(), d.getWindowId(), d.getRestaurantId(), d.getRestaurantName(),
                d.getRestaurantLocation(), d.getWindowName(), d.getCategoryName(), d.getProductName(),
                d.getDescription(), d.getImageUrl(), d.getPrice());
    }
}
