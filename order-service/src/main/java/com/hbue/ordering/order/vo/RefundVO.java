package com.hbue.ordering.order.vo;
import com.hbue.ordering.order.model.OrderRefundDO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/** 学生和审批人共用退款视图，不泄露内部执行租约或重试异常堆栈。 */
public record RefundVO(Long refundId,String refundNo,String requestKey,Long orderId,Long itemId,String productName,Integer quantity,
        BigDecimal amount,String status,String reason,String decisionReason,boolean restock,LocalDateTime createdAt,LocalDateTime dueAt) {
    public static RefundVO from(OrderRefundDO r) {return new RefundVO(r.getRefundId(),r.getRefundNo(),r.getRequestKey(),r.getOrderId(),r.getItemId(),
            r.getProductName(),r.getQuantity(),r.getAmount(),r.getStatus(),r.getReason(),r.getDecisionReason(),Integer.valueOf(1).equals(r.getRestock()),r.getCreatedAt(),r.getDueAt());}
}
