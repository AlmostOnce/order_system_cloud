package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 校验契约必须使用Jakarta运行时注解，IDE静态提示注解不能代替接口参数校验。 */
class BalanceRefundValidationTest {
    @Test void rejectsMissingAmountAndInvalidOriginalOrRefundNumber() {
        try (var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertFalse(validator.validate(new BalanceRefundCommand("PAY1","a".repeat(32),null)).isEmpty());
            assertFalse(validator.validate(new BalanceRefundCommand("bad!","a".repeat(32),BigDecimal.ONE)).isEmpty());
            assertFalse(validator.validate(new BalanceRefundCommand("PAY1","not-a-refund-number",BigDecimal.ONE)).isEmpty());
            assertTrue(validator.validate(new BalanceRefundCommand("PAY1","a".repeat(32),BigDecimal.ONE)).isEmpty());
        }
    }
}
