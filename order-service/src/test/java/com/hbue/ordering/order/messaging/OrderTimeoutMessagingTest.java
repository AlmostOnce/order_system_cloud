package com.hbue.ordering.order.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTimeoutMessagingTest {
    /**
     * 延迟级别必须落在 RocketMQ 默认的 18 档范围内，且不超过剩余时间。
     */
    @Test
    void delayLevelStaysWithinRocketMqLevels() {
        // 15 分钟仍按 10 分钟档（14）投递：多出的第 19 档会被 Broker 判为越界级别而拒收，
        // 到期后由消费端按数据库期限复核并重新投递，不会提前关单。
        assertEquals(14, OrderTimeoutMessaging.delayLevel(900000));
        assertEquals(14, OrderTimeoutMessaging.delayLevel(899000));
        assertEquals(9, OrderTimeoutMessaging.delayLevel(300000));
        assertEquals(1, OrderTimeoutMessaging.delayLevel(1));
        assertEquals(0, OrderTimeoutMessaging.delayLevel(0));
        // 超过 2 小时钳制到最高档 18；更高剩余时间也必须返回合法级别。
        assertEquals(18, OrderTimeoutMessaging.delayLevel(7200000));
        assertEquals(18, OrderTimeoutMessaging.delayLevel(7200001));
        assertEquals(18, OrderTimeoutMessaging.delayLevel(Long.MAX_VALUE / 1000));
    }

    /**
     * 任意剩余时间都只能返回 0 或 1~18 的合法延迟级别，防止再引入越界档位。
     */
    @Test
    void delayLevelNeverExceedsEighteen() {
        for (long millis = 0; millis <= 7_300_000; millis += 7777) {
            int level = OrderTimeoutMessaging.delayLevel(millis);
            assertTrue(level >= 0 && level <= 18, "非法延迟级别：" + level + "，millis=" + millis);
        }
    }
}
