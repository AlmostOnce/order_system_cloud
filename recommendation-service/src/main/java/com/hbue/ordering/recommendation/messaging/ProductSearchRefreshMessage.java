package com.hbue.ordering.recommendation.messaging;

/**
 * 商品服务已提交的目录刷新通知；通知只带键，推荐服务再从商品服务读取当前事实。
 */
public record ProductSearchRefreshMessage(int version, String eventId, Long windowId, Long productId) {
}
