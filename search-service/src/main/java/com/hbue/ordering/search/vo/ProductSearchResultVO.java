package com.hbue.ordering.search.vo;

import java.math.BigDecimal;

public record ProductSearchResultVO(Long productId, Long windowId, Long restaurantId, String restaurantName,
                                  String restaurantLocation, String windowName, String categoryName,
                                  String productName, String description, String imageUrl, BigDecimal price) {
}
