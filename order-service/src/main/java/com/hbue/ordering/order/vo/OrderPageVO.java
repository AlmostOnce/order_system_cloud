package com.hbue.ordering.order.vo;

import java.util.List;

/**
 * 本人订单游标页。不返回总数，不对全部订单执行COUNT或在内存中全量截断。
 * @param items 当前页订单摘要，最多limit条；菜品明细通过详情接口读取
 * @param nextCursor 还有更早订单时为当前页末条orderId的十进制字符串，否则为空
 * @param hasMore 数据库多取一条后判断是否仍有下一页，不由客户端猜测
 */
public record OrderPageVO(List<OrderDetailVO> items, String nextCursor, boolean hasMore) {
}
