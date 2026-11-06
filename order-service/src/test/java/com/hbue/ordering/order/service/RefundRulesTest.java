package com.hbue.ordering.order.service;

import com.hbue.ordering.common.core.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 纯业务规则边界；测试源码随实现交付，当前未授权运行Java测试。 */
class RefundRulesTest {
    /** 前两份向下分摊，最后一份承接余分，累计始终等于实付而非标价。 */
    @Test void splitAmountsRetainLastCentWithoutOverRefunding() {
        var paid = new BigDecimal("10.00");
        assertEquals(new BigDecimal("3.33"), RefundRules.amount(paid,3,0,BigDecimal.ZERO,1));
        assertEquals(new BigDecimal("3.33"), RefundRules.amount(paid,3,1,new BigDecimal("3.33"),1));
        assertEquals(new BigDecimal("3.34"), RefundRules.amount(paid,3,2,new BigDecimal("6.66"),1));
        assertThrows(BusinessException.class, () -> RefundRules.amount(paid,3,2,new BigDecimal("6.66"),2));
        assertThrows(BusinessException.class, () -> RefundRules.amount(paid,3,0,BigDecimal.ZERO,0));
        assertThrows(BusinessException.class, () -> RefundRules.amount(paid,3,3,paid,1));
    }
    /** 精确到期时拒绝，不用售后更新后的updated_at延长申请期。 */
    @Test void completionDeadlineIsExclusiveAndOtherStatesHaveNoInventedDeadline() {
        var completion = LocalDateTime.of(2026,11,6,12,0);
        assertDoesNotThrow(() -> RefundRules.requireApplicable("已完成",completion,completion.plusHours(72).minusNanos(1)));
        assertThrows(BusinessException.class, () -> RefundRules.requireApplicable("已完成",completion,completion.plusHours(72)));
        assertThrows(BusinessException.class, () -> RefundRules.requireApplicable("已完成",null,completion));
        for (String status : new String[]{"已支付","制作中","待取货"})
            assertDoesNotThrow(() -> RefundRules.requireApplicable(status,null,completion.plusDays(8)));
        for (String status : new String[]{"待支付","已取消","已退款"})
            assertThrows(BusinessException.class, () -> RefundRules.requireApplicable(status,null,completion));
    }
}
