package com.hbue.ordering.order.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.dto.CouponConsumeConfirmationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** 供 coupon-service 在本地核销提交后确认订单侧 Outbox 消费状态的内部客户端。 */
@FeignClient(value = "order-service", contextId = "orderCouponConsumeAckClient")
public interface OrderCouponConsumeAckClient {

    /**
     * 仅当事件 ID、订单号和用户券 ID 同时匹配订单侧持久化快照时确认成功。
     * @param token 服务间共享令牌，不能使用学生 JWT 代替
     * @param request 券服务已成功提交的核销事件身份
     * @return 订单服务核验后的统一响应
     */
    @PostMapping("/internal/orders/coupon-consume/confirmed")
    ApiResponse<Void> confirm(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody CouponConsumeConfirmationRequest request
    );
}
