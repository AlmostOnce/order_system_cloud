package com.hbue.ordering.product.messaging;

/** RocketMQ 搜索索引刷新触发事件；载荷不复制商品主数据。 */
public record ProductSearchRefreshEvent(int version, String eventId, Long windowId) {}
