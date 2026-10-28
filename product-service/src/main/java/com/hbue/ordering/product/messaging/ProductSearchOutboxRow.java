package com.hbue.ordering.product.messaging;

/** 待发布的窗口搜索刷新事件。 */
public class ProductSearchOutboxRow {
    private String eventId;
    private Long windowId;
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
}
