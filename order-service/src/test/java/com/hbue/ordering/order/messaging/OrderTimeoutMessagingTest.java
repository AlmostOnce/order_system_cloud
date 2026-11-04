package com.hbue.ordering.order.messaging;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderTimeoutMessagingTest {
    @Test void delayDoesNotRestartFullFifteenMinutesForLateOutbox() {
        assertEquals(19,OrderTimeoutMessaging.delayLevel(900000));
        assertEquals(14,OrderTimeoutMessaging.delayLevel(899000));
        assertEquals(9,OrderTimeoutMessaging.delayLevel(300000));
        assertEquals(1,OrderTimeoutMessaging.delayLevel(1));
        assertEquals(0,OrderTimeoutMessaging.delayLevel(0));
    }
}
