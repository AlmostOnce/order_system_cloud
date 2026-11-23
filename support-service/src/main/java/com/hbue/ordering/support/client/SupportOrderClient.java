package com.hbue.ordering.support.client;

import com.hbue.ordering.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务内部查询客户端。
 *
 * <p>提交用餐问题时校验订单存在且属于当前学生。这里显式透传学生自己的
 * Bearer Token，由订单服务按登录用户校验归属，反馈服务不持有服务级订单权限；
 * 订单不存在、不属于本人或令牌失效都映射为可展示的业务错误。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@FeignClient(name = "order-service", contextId = "supportOrderClient")
public interface SupportOrderClient {

    /**
     * 查询当前学生的订单详情。
     *
     * @param orderId       订单编号
     * @param authorization 学生原始 Authorization 请求头
     * @return 订单快照；订单服务拒绝时通过 HTTP 状态表达
     */
    @GetMapping("/orders/{orderId}")
    ApiResponse<SupportOrderSnapshotVO> getOrder(
            @PathVariable("orderId") Long orderId,
            @RequestHeader("Authorization") String authorization
    );
}
