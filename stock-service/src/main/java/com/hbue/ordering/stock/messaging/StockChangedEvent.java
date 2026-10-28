package com.hbue.ordering.stock.messaging;

/** 已提交的单条库存变动通知，不用于覆盖库存事实数据。 */
public record StockChangedEvent(
        int eventVersion,
        String eventId,
        String bizType,
        String bizRef,
        Long stockId,
        Long windowId,
        Long productId,
        Integer availableQty,
        Integer stockVersion
) {
    public static StockChangedEvent of(StockEventOutboxRow row) {
        return new StockChangedEvent(
                1,
                row.getEventId(),
                row.getBizType(),
                row.getBizRef(),
                row.getStockId(),
                row.getWindowId(),
                row.getProductId(),
                row.getAvailableQty(),
                row.getStockVersion()
        );
    }

    public static String eventId(String bizType, String bizRef, Long stockId) {
        return "stock-flow:" + bizType + ":" + bizRef + ":" + stockId;
    }

    public boolean valid() {
        return eventVersion == 1
                && bizType != null && !bizType.isBlank()
                && bizRef != null && !bizRef.isBlank()
                && stockId != null && stockId > 0
                && windowId != null && windowId > 0
                && productId != null && productId > 0
                && availableQty != null && availableQty >= 0
                && stockVersion != null && stockVersion > 0
                && eventId != null
                && eventId.equals(eventId(bizType, bizRef, stockId));
    }
}
