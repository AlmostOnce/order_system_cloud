package com.hbue.ordering.stock.messaging;

import lombok.Data;

import java.time.LocalDateTime;

/** 待发布库存事件记录。 */
@Data
public class StockEventOutboxRow {
    private String eventId;
    private String bizType;
    private String bizRef;
    private Long stockId;
    private Long windowId;
    private Long productId;
    private Integer availableQty;
    private Integer stockVersion;
    private Integer attempts;
    private LocalDateTime createdAt;
}
