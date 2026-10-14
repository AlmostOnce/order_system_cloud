package com.hbue.ordering.payment.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.dto.request.ChannelNotifyRequest;
import com.hbue.ordering.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付渠道异步通知入口。
 *
 * <p>该入口没有用户 JWT，安全由 PaymentService 按支付单渠道执行的 HMAC 签名与时间窗校验保证；
 * 不在 Gateway 路由白名单内，仅限服务内网访问。
 * 真实渠道（如支付宝）通常要求此处返回约定的纯文本确认，
 * 模拟为保持项目统一响应格式而返回 ApiResponse。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@RestController
@Tag(name = "渠道回调（内部）")
@RequestMapping("/internal/payments")
public class PaymentNotifyController {

    /** 渠道签名请求头名称。 */
    private static final String SIGNATURE_HEADER = "X-Channel-Signature";

    /** 支付业务服务。 */
    private final PaymentService paymentService;

    /**
     * 创建渠道通知入口。
     *
     * @param paymentService 支付业务服务
     */
    public PaymentNotifyController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 接收渠道异步支付结果通知。
     *
     * @param request 渠道通知请求体
     * @param signature 渠道侧签名
     * @return 统一响应；COMMON_000 表示已受理，渠道可停止重试
     */
    @PostMapping("/notify")
    public ApiResponse<Void> notify(
            @Valid @RequestBody ChannelNotifyRequest request,
            @RequestHeader(value = SIGNATURE_HEADER, required = false)
            String signature
    ) {
        return paymentService.handleNotify(request, signature);
    }
}
