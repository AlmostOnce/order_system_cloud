package com.hbue.ordering.notification.dto;

/**
 * 优惠券到期提醒站内信内部请求体。
 *
 * <p>由优惠券服务的到期扫描任务在进入"有效期结束前 24 小时"窗口时调用。
 * {@code eventId} 由券记录编号派生且终身不变，与接收人共同构成通知表唯一约束：
 * 多实例扫描、任务重启或网络重试都只会留下一条提醒。</p>
 *
 * <p>该通知独立于订单与工单：不写 order_id，业务引用使用 {@code COUPON} 类型与
 * 用户券内部编号，消息中心据此跳转到"我的优惠券"。</p>
 *
 * @param recipientId  接收学生内部用户编号
 * @param eventId      幂等事件标识，按券记录派生
 * @param title        通知标题，为空时使用服务端兜底文案
 * @param content      通知正文，为空时使用服务端兜底文案
 * @param userCouponId 用户券记录编号，写入 biz_id 供消息中心跳转
 * @author order-system
 * @date 2026-11-25
 */
public record CouponExpiryNotificationRequest(
        Long recipientId,
        String eventId,
        String title,
        String content,
        Long userCouponId
) {
}
