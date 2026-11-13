package com.hbue.ordering.payment.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.PaymentCloseResult;
import org.springframework.web.bind.annotation.*;

/**
 * 供订单服务关闭支付窗口的内部接口。
 *
 * <p>关单与支付回调可能并发，因此接口返回支付服务持久化的最终结果：若款项已到账，
 * 必须同时返回 payments.success_time，供订单服务正确写入 paid_at；普通关闭则返回 CLOSED 和空时间。
 * X-Internal-Token 是系统间身份边界，接口不经 Gateway 暴露。</p>
 */
@RestController
@RequestMapping("/internal/payments/orders")
public class PaymentCloseInternalController {
    private final PaymentService payments;
    private final InternalScopeTokenVerifier tokens;

    /** 注入关单业务服务和共享密钥校验器；本 Controller 不实现支付状态迁移本身。 */
    public PaymentCloseInternalController(PaymentService payments, InternalScopeTokenVerifier tokens) {
        this.payments = payments;
        this.tokens = tokens;
    }

    /**
     * 关闭指定订单的进行中支付单并返回关单竞争后的真实支付结论。
     *
     * @param orderId 目标订单主键，必须为正数
     * @param token 订单服务携带的内部共享令牌
     * @return PAID 与成功时间，或 CLOSED 与空成功时间
     */
    @PostMapping("/{orderId}/close")
    public ApiResponse<PaymentCloseResult> close(
            @PathVariable("orderId") Long orderId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        tokens.verify(token);
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("订单编号无效");
        return ApiResponse.success(payments.closeUnpaidOrder(orderId));
    }
}
