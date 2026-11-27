package com.hbue.ordering.order.vo;

/**
 * 消费事实查询结果。
 *
 * <p>只返回布尔结论，不回传订单号、金额、时间或菜品明细。评论服务只关心
 * "能不能评论"，多返回任何交易字段都会扩大内部接口的数据暴露面。</p>
 *
 * @param purchased 是否存在符合口径的已完成消费记录
 * @author order-system
 * @date 2026-11-25
 */
public record PurchaseFactVO(boolean purchased) {
}
