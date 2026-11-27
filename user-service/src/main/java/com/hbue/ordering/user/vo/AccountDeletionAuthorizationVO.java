package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证通过后签发的一次性注销授权视图。
 *
 * <p>授权令牌只保存在 Redis（保存摘要）中，10 分钟内必须用于提交注销，
 * 提交成功或校验失败次数耗尽后立即失效。响应同时返回生效时长，便于前端提示。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionAuthorizationVO {

    /**
     * 一次性注销授权令牌明文。
     */
    private String authorizationToken;

    /**
     * 令牌有效秒数。
     */
    private long expiresIn;
}
