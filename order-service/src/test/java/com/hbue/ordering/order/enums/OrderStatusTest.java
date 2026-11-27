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
     * 商家只能按履约顺序推进状态，不能代替支付回调标记已支付。
     */
    @Test
    void shouldAllowManagerFulfillmentTransitionsButNotPaymentTransition() {
        assertTrue(OrderStatus.PAID
                .canBeAdvancedByManagerTo(OrderStatus.PREPARING));
        assertTrue(OrderStatus.PREPARING
                .canBeAdvancedByManagerTo(OrderStatus.WAITING_FOR_PICKUP));
        assertFalse(OrderStatus.WAITING_FOR_PICKUP
                .canBeAdvancedByManagerTo(OrderStatus.COMPLETED));
        assertFalse(OrderStatus.PENDING_PAYMENT
                .canBeAdvancedByManagerTo(OrderStatus.PAID));
        assertFalse(OrderStatus.PAID
                .canBeAdvancedByManagerTo(OrderStatus.COMPLETED));
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
