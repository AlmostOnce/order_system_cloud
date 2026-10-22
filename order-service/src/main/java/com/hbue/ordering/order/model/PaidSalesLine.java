package com.hbue.ordering.order.model;

import lombok.Data;

@Data
public class PaidSalesLine {
    private Long orderId;
    private Long itemId;
    private Long productId;
    private Long windowId;
    private String productName;
    private Integer quantity;
}
