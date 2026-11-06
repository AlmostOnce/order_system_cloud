package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.notification.messaging.OrderNotificationEvent;
import com.hbue.ordering.notification.mapper.NotificationMapper;
import com.hbue.ordering.notification.service.OrderNotificationService;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.hbue.ordering.notification.websocket.NotificationSocketBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订单状态事件落库服务。数据库唯一约束吸收MQ重投，只有本地通知事务提交后才尝试在线提示。
 * 实时推送失败保留数据库通知，用户可通过历史和未读查询补齐，不撤销已经发生的订单事实。
 */
@Service
public class OrderNotificationServiceImpl implements OrderNotificationService {
    private final NotificationMapper repository;
    private final NotificationSocketBus socketBus;
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationServiceImpl.class);
    /** 注入通知Mapper和在线提示总线；入库与提交后提示的顺序由accept方法事务保证。 */
    public OrderNotificationServiceImpl(NotificationMapper repository, NotificationSocketBus socketBus) { this.repository = repository; this.socketBus = socketBus; }
    /**
     * 幂等持久化已提交的订单状态事件。注册事务提交后的提示回调；只有确认为同事件重投才吸收唯一键异常。
     */
    @Transactional
    public void accept(OrderNotificationEvent event) {
        if (event == null || !event.valid()) throw new IllegalArgumentException("订单通知事件不正确");
        try {
            repository.insertOrderEvent(UUID.randomUUID().toString(), event.recipientId(), event.eventId(),
                    event.orderId(), "退款成功".equals(event.status()) ? "订单菜品退款已到账，请查看退款进度" :
                            "退款申请被拒绝".equals(event.status()) ? "退款申请未通过，请查看处理原因或申请介入" : "订单状态已更新为" + event.status());
            // 必须等待通知记录提交；事务回滚时不会向客户端发送虚假的通知变化。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { socketBus.publish(event.recipientId()); }
                    catch (RuntimeException error) { log.warn("实时通知发送失败，用户可通过列表补齐"); }
                }
            });
        } catch (DuplicateKeyException error) {
            // 只吞掉已确认的来源事件重投，其他唯一约束错误仍交给MQ重试。
            if (!repository.existsEvent(event.recipientId(), event.eventId())) throw error;
        }
    }
}
