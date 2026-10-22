package com.hbue.ordering.order.ranking;

import lombok.Data;

@Data
public class SalesOutboxRow {
    private String eventId;
    private Long orderId;
    private String payload;
}
