package com.hbue.ordering.order.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.client.fallback.OrderServiceClientFallbackFactory;
import com.hbue.ordering.order.api.vo.OrderCartSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 订单详情远程读取契约，通过 Nacos 服务名调用，不绕过订单归属校验。
 * 调用方必须透传当前用户的 Bearer Token，不使用伪造用户 ID 的内部请求。
 */
@FeignClient(name = "order-service", contextId = "cartOrderServiceClient",
        fallbackFactory = OrderServiceClientFallbackFactory.class)
public interface OrderServiceClient {
    /**
     * 读取当前用户自己订单的购买事实；不存在或越权由服务端统一返回 404。
     * @param orderId 已创建的订单 ID
     * @return 订单详情中用于清理的字段
     */
    @GetMapping("/orders/{orderId}")
    ApiResponse<OrderCartSnapshotVO> getOrder(@PathVariable("orderId") Long orderId);
}
