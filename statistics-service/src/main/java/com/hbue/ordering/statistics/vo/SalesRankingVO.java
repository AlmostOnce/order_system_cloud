package com.hbue.ordering.statistics.vo;

import java.util.List;

/** 学生端累计菜品销量榜响应，不包含用户或订单标识。 */
public record SalesRankingVO(String updatedAt, List<Item> items) {
    /** 一项按售出份数排序的菜品。 */
    public record Item(int rank, Long productId, Long windowId, String productName, long sales) {
    }
}
