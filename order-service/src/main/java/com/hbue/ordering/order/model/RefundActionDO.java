package com.hbue.ordering.order.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/** 不可覆盖的审批轨迹，数据库仅追加，记录系统自动处理和商家/经理决定。 */
@Data @TableName("order_refund_actions")
public class RefundActionDO {
    @TableId(type=IdType.AUTO) private Long actionId;
    /** 关联退款单。 */ private Long refundId;
    /** 系统处理为空，不伪造管理员身份。 */ private Long actorId;
    /** 工作流动作码。 */ private String action;
    /** 操作说明，不记录令牌或支付凭据。 */ private String detail;
    /** 数据库UTC+8业务时间。 */ private LocalDateTime createdAt;
}
