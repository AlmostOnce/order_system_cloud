package com.hbue.ordering.payment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建支付单请求。
 *
 * @author order-system
 * @date 2026-10-12
 */
@Data
public class PaymentCreateRequest {

    /** 待支付订单 ID。 */
    @NotNull
    private Long orderId;

    /**
     * 支付渠道标识，缺省按 MOCK 处理。
     *
     * <p>取值必须是已注册的渠道：{@code MOCK} 模拟第三方渠道、
     * {@code BALANCE} 账户余额。渠道不存在时返回 400。</p>
     */
    private String channelCode;

    /**
     * 支付密码，仅余额渠道必填。
     *
     * <p>与登录密码完全分离的第二道凭据，只在本次请求的请求体中传入，
     * 用后即弃，不写入日志、不进入 JWT。</p>
     */
    private String payPassword;
}
