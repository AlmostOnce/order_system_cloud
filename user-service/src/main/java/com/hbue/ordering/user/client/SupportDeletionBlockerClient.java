package com.hbue.ordering.user.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.client.vo.SupportDeletionBlockersVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 工单服务账号注销阻断项查询客户端。
 *
 * <p>只在用户服务内部使用，必须携带共享内部令牌；远程失败同样按 fail-closed 处理。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@FeignClient(name = "support-service", contextId = "supportDeletionBlockerClient")
public interface SupportDeletionBlockerClient {

    /**
     * 查询用户维度的未关闭工单数量。
     *
     * @param userId        用户 ID
     * @param internalToken 服务间共享内部令牌
     * @return 统一响应中的工单计数
     */
    @GetMapping("/internal/users/{userId}/account-deletion/blockers")
    ApiResponse<SupportDeletionBlockersVO> getBlockers(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
