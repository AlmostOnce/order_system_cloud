package com.hbue.ordering.payment.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付单状态机测试。
 */
class PaymentStatusTest {

    @Test
    void shouldAllowCreatedToSuccessOrFailed() {
        assertTrue(PaymentStatus.CREATED
                .canTransitionTo(PaymentStatus.SUCCESS));
        assertTrue(PaymentStatus.CREATED
                .canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void shouldForbidTransitionFromTerminalStates() {
        assertFalse(PaymentStatus.SUCCESS
                .canTransitionTo(PaymentStatus.FAILED));
        assertFalse(PaymentStatus.SUCCESS
                .canTransitionTo(PaymentStatus.CREATED));
        assertFalse(PaymentStatus.FAILED
                .canTransitionTo(PaymentStatus.SUCCESS));
        assertFalse(PaymentStatus.CLOSED
                .canTransitionTo(PaymentStatus.SUCCESS));
    }

    @Test
    void shouldRejectNullAndUnknownCode() {
        assertNull(PaymentStatus.findByCode(null));
        assertNull(PaymentStatus.findByCode("UNKNOWN"));
    }

    @Test
    void shouldFindStatusByCode() {
        assertEquals(
                PaymentStatus.CREATED,
                PaymentStatus.findByCode("CREATED")
        );
        assertEquals(
                PaymentStatus.SUCCESS,
                PaymentStatus.findByCode("SUCCESS")
        );
    }
}
