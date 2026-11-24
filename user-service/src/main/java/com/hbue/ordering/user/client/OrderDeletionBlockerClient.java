package com.hbue.ordering.user.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.client.vo.OrderDeletionBlockersVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 订单服务账号注销阻断项查询客户端。
 *
 * <p>只在用户服务内部使用，必须携带共享内部令牌。该接口只返回计数，不返回订单详情；
 * 调用方把任何远程失败视为“无法确认”，按 fail-closed 拒绝注销申请或到期删除。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@FeignClient(name = "order-service", contextId = "orderDeletionBlockerClient")
public interface OrderDeletionBlockerClient {

    /**
     * 查询用户维度的订单与退款阻断计数。
     *
     * @param userId        用户 ID，必须是已通过认证的本人
     * @param internalToken 服务间共享内部令牌
     * @return 统一响应中的阻断计数
     */
    @GetMapping("/internal/users/{userId}/account-deletion/blockers")
    ApiResponse<OrderDeletionBlockersVO> getBlockers(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
