package com.hbue.ordering.coupon.client.vo;

/**
 * 通知服务优惠券到期提醒请求。
 *
 * <p>由券到期扫描任务发送。{@code eventId} 按用户券记录派生且终身不变：
 * 任务重启、多实例并发或网络重试都只会产生一条提醒，服务端唯一约束
 * {@code (recipient_id, event_id)} 是最终防线。</p>
 *
 * @param recipientId  接收学生内部用户编号
 * @param eventId      幂等事件标识，格式为 {@code coupon-expiry:{userCouponId}}
 * @param title        通知标题
 * @param content      通知正文，包含券名与具体到期时间
 * @param userCouponId 用户券记录编号，通知服务写入业务引用供消息中心跳转
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
