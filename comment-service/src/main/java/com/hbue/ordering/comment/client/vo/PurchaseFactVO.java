package com.hbue.ordering.comment.client.vo;

/**
 * 订单服务返回的消费事实结论。
 *
 * <p>只承载布尔结果：订单号、金额与明细属于订单服务，评论服务无权也不需要获取。
 * 字段缺失时按 false 处理，由准入服务拒绝评论，不能默认放行。</p>
 *
 * @param purchased 是否存在符合口径的有效消费记录
 * @author order-system
 * @date 2026-11-25
 */
public record PurchaseFactVO(boolean purchased) {
}
