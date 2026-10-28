package com.hbue.ordering.search.messaging;

/** 商品服务 outbox 发布的搜索窗口刷新事件。 */
public record ProductSearchRefreshEvent(int version, String eventId, Long windowId) {
    public boolean valid() { return version == 1 && eventId != null && !eventId.isBlank() && windowId != null && windowId > 0; }
}
