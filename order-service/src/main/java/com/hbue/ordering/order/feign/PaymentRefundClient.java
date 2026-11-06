package com.hbue.ordering.order.feign;
import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
/** 订单退款任务调用支付服务，超时不视为失败到账，下一次使用相同退款号重试。 */
@FeignClient(name="payment-service",contextId="orderPaymentRefund")
public interface PaymentRefundClient {
    /** 支付服务确认的最小实付事实，不包含支付密码或渠道敏感信息。 */
    record Fact(Long userId,BigDecimal paidAmount,String channelCode) {}
    /** 稳定退款号及订单服务实付计算金额，支付单号由支付服务自行解析。 */
    record Command(String refundNo,BigDecimal amount) {}
    /** 申请时交叉核验支付所属人、金额与余额渠道，失败时不能进入退款队列。 */
    @GetMapping("/internal/payments/orders/{orderId}/refund-fact")
    ApiResponse<Fact> fact(@PathVariable("orderId") Long orderId,@RequestHeader("X-Internal-Token") String token);
    /** 执行任务按相同退款号重试，不设置成功降级，不把超时解释为未入账。 */
    @PostMapping("/internal/payments/orders/{orderId}/refund")
    ApiResponse<String> refund(@PathVariable("orderId") Long orderId,@RequestHeader("X-Internal-Token") String token,@RequestBody Command command);
}
