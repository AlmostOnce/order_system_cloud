package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号注销冷静期撤回令牌视图对象。
 *
 * <p>待注销账号登录时不会获得正常访问令牌，只能凭本对象中的一次性撤回令牌调用撤回接口。
 * 令牌只返回给通过登录密码校验的本人，服务端只保存令牌摘要；有效期由
 * {@link #expiresIn} 秒数声明，过期或使用一次后立即失效。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionCancelTokenVO {

    /**
     * 一次性撤回令牌明文。
     *
     * <p>仅在签发响应中出现，不写入日志、不落库；客户端应只保存在内存中。</p>
     */
    private String cancelToken;

    /**
     * 令牌剩余有效秒数。
     */
    private long expiresIn;
}
