package com.hbue.ordering.coupon.messaging;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.coupon.api.dto.CouponPaidEvent;
import com.hbue.ordering.coupon.service.CouponService;
import com.hbue.ordering.order.api.client.OrderCouponConsumeAckClient;
import com.hbue.ordering.order.api.dto.CouponConsumeConfirmationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * RocketMQ 支付成功事件入口；函数名称与 coupon-mq profile 中的 binding 对应。
 */
@Configuration
public class CouponPaidMessaging {
    /**
     * 先提交券服务本地核销，再确认订单 Outbox；任一步骤失败都不确认消息，等待 RocketMQ 重试。
     * 若消息进入 DLQ，订单 Outbox 会在消费回执超时后重新发布；券状态迁移本身按订单幂等。
     */
    @Bean
    public Consumer<CouponPaidEvent> couponPaid(
            CouponService couponService,
            OrderCouponConsumeAckClient orderAckClient,
            @Value("${internal.scope.token:}") String internalScopeToken
    ) {
        return event -> {
            couponService.markUsed(event);
            ApiResponse<Void> response = orderAckClient.confirm(internalScopeToken,
                    new CouponConsumeConfirmationRequest(event.eventId(), event.orderNo(), event.userCouponId()));
            if (response == null || !CommonErrorCode.SUCCESS.code().equals(response.getCode())) {
                throw new IllegalStateException("订单服务未确认优惠券核销 Outbox 回执");
            }
        };
    }
}
