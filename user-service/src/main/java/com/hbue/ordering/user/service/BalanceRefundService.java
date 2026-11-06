package com.hbue.ordering.user.service;
import com.hbue.ordering.user.api.dto.BalanceRefundCommand;
/** 仅退回已经发生的余额扣款；同一退款号不重复入账，累计不超过原扣款。 */
public interface BalanceRefundService { String refund(Long userId, BalanceRefundCommand command); }
