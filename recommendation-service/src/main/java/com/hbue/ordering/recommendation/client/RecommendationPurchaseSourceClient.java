package com.hbue.ordering.recommendation.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.recommendation.RecommendationPurchaseSnapshotPageVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务为推荐服务提供的受保护历史购买快照游标契约。
 */
@FeignClient(name = "order-service", contextId = "recommendationPurchaseSourceClient")
public interface RecommendationPurchaseSourceClient {
    /**
     * 按订单主键升序读取已支付订单的完整当前快照。
     *
     * @param token        订单服务现有 X-Internal-Token，不复用普通内部作用域令牌
     * @param afterOrderId 上次成功投影的订单游标
     * @param limit        每页订单上限，调用方固定为100
     * @return 订单服务同一读快照中的头与全部明细
     */
    @GetMapping("/internal/orders/recommendation-purchase-snapshots")
    ApiResponse<RecommendationPurchaseSnapshotPageVO> page(
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam("afterOrderId") long afterOrderId,
            @RequestParam("limit") int limit);
}
