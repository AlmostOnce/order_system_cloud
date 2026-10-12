package com.hbue.ordering.payment.feign;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.payment.dto.OrderInternalDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单服务内部客户端（payment-service 专用）。
 *
 * <p>刻意不配置 fallbackFactory：回调链路中订单推进失败必须显式感知
 * 并留痕（支付单保持 SUCCESS，人工核查），不能被降级逻辑吞掉；
 * 也不透传用户 JWT——该调用是支付回调驱动的系统事件，无用户上下文，
 * 认证由 X-Internal-Token 共享密钥完成（见 PaymentFeignConfig）。</p>
 *
 * @author order-system
 * @date 2026-10-12
 */
@FeignClient(name = "order-service")
public interface OrderInternalClient {

    /**
     * 查询订单内部视图，用于创建支付单时校验归属与状态。
     *
     * @param orderId 订单 ID
     * @return 内部订单信息
     */
    @GetMapping("/internal/orders/{orderId}")
    ApiResponse<OrderInternalDTO> getOrder(
            @PathVariable("orderId") Long orderId
    );

    /**
     * 支付成功后推进订单状态：待支付到已支付。
     *
     * @param orderId 订单 ID
     * @return 订单状态变更结果；订单已取消等冲突场景返回 CONFLICT 码
     */
    @PatchMapping("/internal/orders/{orderId}/paid")
    ApiResponse<Void> markPaid(
            @PathVariable("orderId") Long orderId
    );
}
