package com.hbue.ordering.notification.service.impl;

import com.hbue.ordering.notification.messaging.OrderNotificationEvent;
import com.hbue.ordering.notification.repository.NotificationRepository;
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

@Service
public class OrderNotificationServiceImpl implements OrderNotificationService {
    private final NotificationRepository repository;
    private final NotificationSocketBus socketBus;
    private static final Logger log = LoggerFactory.getLogger(OrderNotificationServiceImpl.class);
    public OrderNotificationServiceImpl(NotificationRepository repository, NotificationSocketBus socketBus) { this.repository = repository; this.socketBus = socketBus; }
    @Transactional
    public void accept(OrderNotificationEvent event) {
        if (event == null || !event.valid()) throw new IllegalArgumentException("订单通知事件不正确");
        try {
            repository.insertOrderEvent(UUID.randomUUID().toString(), event.recipientId(), event.eventId(),
                    event.orderId(), "订单状态已更新为" + event.status());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { socketBus.publish(event.recipientId()); }
                    catch (RuntimeException error) { log.warn("实时通知发送失败，用户可通过列表补齐"); }
                }
            });
        } catch (DuplicateKeyException error) {
            if (!repository.existsEvent(event.recipientId(), event.eventId())) throw error;
        }
    }
}
