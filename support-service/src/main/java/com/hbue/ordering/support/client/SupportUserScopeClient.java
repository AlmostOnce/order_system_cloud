package com.hbue.ordering.support.client;

import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.support.client.vo.SupportUserScopeVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 用户数据范围内部客户端。
 *
 * <p>管理端每次受理、回复、备注和关闭前都读取操作者的最新数据范围，
 * 不在本地缓存授权结果，避免管理员调整范围后旧授权继续生效。
 * 使用共享内部令牌，不携带用户 JWT。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
@FeignClient(name = "user-service", contextId = "supportUserScopeClient")
public interface SupportUserScopeClient {

    /**
     * 查询指定员工的数据范围。
     *
     * @param userId        员工内部用户编号
     * @param internalToken 服务间共享密钥
     * @return 平台、窗口与餐厅范围
     */
    @GetMapping("/internal/users/{userId}/scopes")
    ApiResponse<SupportUserScopeVO> getScopes(
            @PathVariable("userId") Long userId,
            @RequestHeader("X-Internal-Token") String internalToken
    );
}
