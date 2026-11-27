package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.messaging.OrderNotificationEvent;

/**
 * 订单通知事件处理契约。只消费已经由订单服务确认提交的业务事件，保持接收用户与来源事件幂等。
 */
public interface OrderNotificationService {
    /**
     * 幂等持久化已提交的订单状态事件。注册事务提交后的提示回调；只有确认为同事件重投才吸收唯一键异常。
     */
    void accept(OrderNotificationEvent event);
}
