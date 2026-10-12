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
}
