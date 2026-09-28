package com.hbue.ordering.order.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态流转规则测试。
 *
 * @author order-system
 * @date 2026-09-28
 */
class OrderStatusTest {

    /**
     * 待支付订单只允许支付成功或顾客取消。
     */
    @Test
    void shouldAllowOnlyPaymentOrCancellationFromPendingPayment() {
        assertTrue(OrderStatus.PENDING_PAYMENT
                .canTransitionTo(OrderStatus.PAID));
        assertTrue(OrderStatus.PENDING_PAYMENT
                .canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.PENDING_PAYMENT
                .canTransitionTo(OrderStatus.PREPARING));
    }

    /**
     * 管理员只能按履约顺序推进状态，不能代替支付回调标记已支付。
     */
    @Test
    void shouldAllowAdminFulfillmentTransitionsButNotPaymentTransition() {
        assertTrue(OrderStatus.PAID
                .canBeAdvancedByAdminTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING
                .canBeAdvancedByAdminTo(OrderStatus.WAITING_FOR_PICKUP));
        assertTrue(OrderStatus.WAITING_FOR_PICKUP
                .canBeAdvancedByAdminTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.PENDING_PAYMENT
                .canBeAdvancedByAdminTo(OrderStatus.PAID));
        assertFalse(OrderStatus.PAID
                .canBeAdvancedByAdminTo(OrderStatus.COMPLETED));
    }

    /**
     * 已完成和已取消订单不能再次流转。
     */
    @Test
    void shouldKeepCompletedAndCancelledOrdersTerminal() {
        assertFalse(OrderStatus.COMPLETED
                .canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.CANCELLED
                .canTransitionTo(OrderStatus.PENDING_PAYMENT));
    }
}
