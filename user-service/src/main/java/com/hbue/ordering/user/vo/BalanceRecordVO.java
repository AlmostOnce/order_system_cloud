package com.hbue.ordering.user.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 本人可见的余额流水；金额为正数，收入/支出方向由变动类型标识。 */
public record BalanceRecordVO(Long recordId, String changeType, BigDecimal amount,
                              BigDecimal balanceAfter, String bizRef, String remark,
                              LocalDateTime createdAt) {
}
