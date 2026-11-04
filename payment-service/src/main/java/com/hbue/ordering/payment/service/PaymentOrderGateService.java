package com.hbue.ordering.payment.service;
import java.util.function.Supplier;
public interface PaymentOrderGateService {
    <T> T whileOpen(Long orderId, Supplier<T> action);
    void beginClose(Long orderId);
}
