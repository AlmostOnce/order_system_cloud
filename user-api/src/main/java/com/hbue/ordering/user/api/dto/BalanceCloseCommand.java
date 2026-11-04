package com.hbue.ordering.user.api.dto;
import java.math.BigDecimal;
public record BalanceCloseCommand(String bizType, String bizRef, BigDecimal amount) {}
