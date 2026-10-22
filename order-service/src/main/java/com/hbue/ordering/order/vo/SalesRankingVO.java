package com.hbue.ordering.order.vo;

import java.util.List;

/** 全站菜品累计销量榜，不包含任何下单用户信息。 */
public record SalesRankingVO(String updatedAt, List<Item> items) {
    public record Item(int rank, Long productId, Long windowId, String productName, long sales) {}
}
