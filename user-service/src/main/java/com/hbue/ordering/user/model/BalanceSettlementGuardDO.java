package com.hbue.ordering.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("balance_settlement_guard")
public class BalanceSettlementGuardDO {
    @TableId(type = IdType.AUTO)
    private Long guardId;
    private Long userId;
    private String bizType;
    private String bizRef;
    private BigDecimal amount;
    private Integer closed;
}
