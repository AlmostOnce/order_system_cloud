package com.hbue.ordering.notification.messaging;

import java.util.Set;

/**
 * 来自订单服务的通知事件契约。事件合法性包括来源标识、订单、接收人和受支持状态，非法事件不得落库。
 */
public record OrderNotificationEvent(int version, String eventId, Long orderId, Integer orderVersion,
        Long recipientId, String status) {
    private static final Set<String> ALLOWED = Set.of("已支付", "制作中", "待取货", "已完成", "已取消", "已退款", "退款成功", "退款申请被拒绝");
    /**
     * 检查MQ事件的标识、接收人、订单及状态是否合法；校验业务事件格式，不承担用户登录凭据验证。
     */
    public boolean valid() {
        return version == 1 && orderId != null && orderId > 0 && orderVersion != null && orderVersion > 0
                && recipientId != null && recipientId > 0 && status != null && ALLOWED.contains(status)
                && ("order-status:" + orderId + ":" + orderVersion).equals(eventId);
    }
}
