package com.hbue.ordering.order.model;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** 退款持久化对象；每笔对应一个订单菜品和数量，业务规则集中在退款Service。 */
@Data @TableName("order_refunds")
public class OrderRefundDO {
    /** 退款主键。 */
    @TableId(type=IdType.AUTO)
    private Long refundId;
    /** 跨服务稳定幂等号。 */
    private String refundNo;
    /** 学生申请幂等键。 */
    private String requestKey;
    /** 订单主键。 */
    private Long orderId;
    /** 订单所属学生。 */
    private Long userId;
    /** 订单菜品明细主键。 */
    private Long itemId;
    /** 原菜品主键。 */
    private Long productId;
    /** 下单菜品名称快照。 */
    private String productName;
    /** 本次申请份数。 */
    private Integer quantity;
    /** 服务端计算的退款金额。 */
    private BigDecimal amount;
    /** 学生申请原因。 */
    private String reason;
    /** 退款工作流状态。 */
    private String status;
    /** 申请时订单状态。 */
    private String originalStatus;
    /** 批准是否回库存，完成订单恒为零。 */
    private Integer restock;
    /** 最近审批说明，历史另存轨迹。 */
    private String decisionReason;
    /** 最后审批人，系统操作为空。 */
    private Long decidedBy;
    /** 商家72小时截止时间。 */
    private LocalDateTime dueAt;
    /** 下次重试或任务租约到期。 */
    private LocalDateTime nextAttemptAt;
    /** 执行次数。 */
    private Integer attempts;
    /** 申请时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
