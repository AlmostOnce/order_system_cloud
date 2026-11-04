package com.hbue.ordering.user.model;
import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import lombok.Data;
@Data
@TableName("balance_settlement_guard")
public class BalanceSettlementGuardDO {
    @TableId(type=IdType.AUTO) private Long guardId;
    private Long userId;
    private String bizType;
    private String bizRef;
    private BigDecimal amount;
    private Integer closed;
}
