package com.hbue.ordering.order.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.vo.OrderPaidSalesSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 统计服务兼容旧版销量消息时使用的订单内部只读契约。
 *
 * <p>路径仅在订单服务直连开放，调用必须显式传入 X-Internal-Token；消费者不得跨服务直连 order_db。</p>
 */
@FeignClient(name = "order-service", contextId = "statisticsOrderSalesClient")
public interface OrderSalesInternalClient {
    /** 查询订单支付时固化的销量快照。 */
    @GetMapping("/internal/orders/{orderId}/paid-sales-snapshot")
    ApiResponse<OrderPaidSalesSnapshotVO> getPaidSalesSnapshot(
            @PathVariable("orderId") Long orderId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
