package com.hbue.ordering.user.api.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.fallback.UserScopeInternalClientFallbackFactory;
import com.hbue.ordering.user.api.vo.UserScopeVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户数据范围内部调用客户端。
 *
 * <p>供业务服务查询某用户的数据范围，属于服务间内部调用，使用共享密钥
 * {@code X-Internal-Token} 鉴权，不携带用户 JWT。
 * 该接口不在 Gateway 路由白名单内，外部无法直接访问。调用方只能把该响应作为范围事实，
 * 不得把查询失败解释为空范围或默认放行；业务服务负责缓存和最终授权判定。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@FeignClient(
        name = "user-service",
        contextId = "userScopeInternalClient",
        fallbackFactory = UserScopeInternalClientFallbackFactory.class
)
public interface UserScopeInternalClient {

    /**
     * 查询指定用户的数据范围。
     *
     * @param userId 用户 ID
     * @param internalToken 服务间共享密钥
     * @return 统一响应中的平台、档口和餐厅范围；调用失败由 fallback 返回服务不可用响应
     */
    @GetMapping("/internal/users/{userId}/scopes")
    ApiResponse<UserScopeVO> getScopes(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
