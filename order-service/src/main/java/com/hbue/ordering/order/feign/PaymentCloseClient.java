package com.hbue.ordering.order.feign;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.vo.PaymentCloseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", contextId = "orderPaymentClose")
public interface PaymentCloseClient {
    @PostMapping("/internal/payments/orders/{orderId}/close")
    ApiResponse<PaymentCloseVO> close(@PathVariable("orderId") Long orderId, @RequestHeader("X-Internal-Token") String token);

    /**
     * 撤销支付入口关闭意图。
     *
     * <p>订单取消在关单之后还可能因回补库存、释放优惠券失败而整体回滚；此时订单仍是待支付，
     * 必须撤销支付库里的关闭标记，否则用户无法继续支付。该调用幂等，可安全重试。</p>
     *
     * @param orderId 订单主键
     * @param token   内部共享令牌
     * @return 空响应
     */
    @PostMapping("/internal/payments/orders/{orderId}/reopen")
    ApiResponse<Void> reopen(@PathVariable("orderId") Long orderId, @RequestHeader("X-Internal-Token") String token);
}
