package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.messaging.OrderNotificationEvent;

public interface OrderNotificationService {
    void accept(OrderNotificationEvent event);
}
