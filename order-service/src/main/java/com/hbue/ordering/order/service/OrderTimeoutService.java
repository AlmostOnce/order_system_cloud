package com.hbue.ordering.order.service;
public interface OrderTimeoutService { void expire(Long orderId); long remainingMillis(Long orderId); }
