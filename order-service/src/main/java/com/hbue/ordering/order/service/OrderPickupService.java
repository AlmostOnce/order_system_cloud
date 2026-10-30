package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.OrderStatusVO;

public interface OrderPickupService {
    String getOwnPickupCode(Long userId, Long orderId);
    OrderStatusVO verifyPickup(Long operatorId, String pickupCode);
}
