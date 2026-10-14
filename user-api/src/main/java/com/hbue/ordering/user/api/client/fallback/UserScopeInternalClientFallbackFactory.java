package com.hbue.ordering.user.api.client.fallback;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.user.api.client.UserScopeInternalClient;
import com.hbue.ordering.user.api.vo.UserScopeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 用户数据范围内部调用降级工厂。
 *
 * <p>范围查询失败时返回服务不可用，交由调用方拒绝本次受保护的数据操作；
 * 降级实现不伪造空范围或平台级范围，也不将依赖故障转换为放行。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Component
public class UserScopeInternalClientFallbackFactory
        implements FallbackFactory<UserScopeInternalClient> {

    /**
     * 日志对象。
     */
    private static final Logger log =
            LoggerFactory.getLogger(UserScopeInternalClientFallbackFactory.class);

    /**
     * 创建用户数据范围降级客户端。
     *
     * <p>工厂保留原始失败原因供日志排查，返回失败响应要求调用方 fail closed；
     * 调用方不能因响应缺少范围数据就继续执行业务。</p>
     *
     * @param cause 远程调用失败原因
     * @return 用户数据范围降级客户端
     */
    @Override
    public UserScopeInternalClient create(Throwable cause) {
        // 范围查询失败属于系统故障，记录异常便于排查。
        log.error("调用 user-service 查询数据范围失败", cause);

        return (userId, internalToken) -> ApiResponse.failure(
                CommonErrorCode.SERVICE_UNAVAILABLE,
                "用户服务暂时不可用"
        );
    }
}
