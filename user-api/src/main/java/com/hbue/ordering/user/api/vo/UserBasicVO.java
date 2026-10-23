package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户基础信息视图对象。
 *
 * <p>该对象是用户服务返回给认证服务及业务服务的最小用户信息契约，只包含用户标识、用户名和角色编码；
 * 不包含密码、支付凭据、余额等敏感或非必要字段，不能替代用户服务内部持久化对象。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBasicVO {

    /**
     * 用户 ID。
     */
    private Long userId;

    /** 用户对外 UUID，仅服务间基础视图同时保留内部主键。 */
    private String userUuid;

    /** 永久登录用户号。 */
    private String userNo;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 角色编码。
     */
    private String roleCode;
    public UserBasicVO(Long userId, String username, String roleCode) {
        this(userId, null, null, username, roleCode);
    }
}
