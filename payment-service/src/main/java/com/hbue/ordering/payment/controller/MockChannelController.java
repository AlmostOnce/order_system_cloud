package com.hbue.ordering.payment.controller;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.dto.request.MockPayRequest;
import com.hbue.ordering.payment.service.PaymentService;
import com.hbue.ordering.payment.vo.CheckoutInfoVO;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 模拟第三方支付渠道入口（仅 dev 环境启用）。
 *
 * <p>该控制器扮演"渠道方"角色：模拟收银台页面与"确认支付"按钮。
 * 刻意不使用 Feign 与服务发现——第三方渠道本就不在微服务体系内；
 * 回调通过真实 HTTP 发往支付服务回调入口并携带 HMAC 签名。</p>
 *
 * <p>路径前缀 /mock/** 不在 Gateway 路由白名单内，仅限 9101 直连访问。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@RestController
@RequestMapping("/mock/channel")
@Profile("dev")
public class MockChannelController {

    /** 支付业务服务。 */
    private final PaymentService paymentService;

    /**
     * 创建模拟渠道入口。
     *
     * @param paymentService 支付业务服务
     */
    public MockChannelController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 模拟收银台页面信息。
     *
     * @param paymentNo 业务支付单号
     * @return 收银台信息
     */
    @GetMapping("/checkout/{paymentNo}")
    public ApiResponse<CheckoutInfoVO> checkout(
            @PathVariable("paymentNo") String paymentNo
    ) {
        return ApiResponse.success(paymentService.getCheckoutInfo(paymentNo));
    }

    /**
     * 模拟"我支付成功了"确认按钮。
     *
     * <p>body 可选：缺省按支付成功全额处理；
     * paidAmount 参数专门用于演练金额不符场景。
     * 确认后由异步线程向回调入口发起带签名通知（模拟渠道的异步性）。</p>
     *
     * @param paymentNo 业务支付单号
     * @param request 确认支付请求（可为空 body）
     * @return 渠道交易号
     */
    @PostMapping("/pay/{paymentNo}")
    public ApiResponse<Map<String, String>> pay(
            @PathVariable("paymentNo") String paymentNo,
            @Valid @RequestBody(required = false) MockPayRequest request
    ) {
        String channelTradeNo =
                paymentService.confirmMockPayment(paymentNo, request);
        return ApiResponse.success(
                Map.of(
                        "paymentNo", paymentNo,
                        "channelTradeNo", channelTradeNo
                )
        );
    }
}
