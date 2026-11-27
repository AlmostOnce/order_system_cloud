package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户基础信息视图对象。
 *
 * <p>该对象是用户服务返回给认证服务及业务服务的最小用户信息契约，只包含用户标识、用户名、角色编码及内部登录凭据版本；
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

    /**
     * 内部会话校验版本，不含凭据。
     */
    private long credentialVersion;

    private boolean emailVerified;

    /**
     * 用户对外 UUID，仅服务间基础视图同时保留内部主键。
     */
    private String userUuid;

    /**
     * 永久登录用户号。
     */
    private String userNo;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 角色编码。
     */
    private String roleCode;

    /**
     * 是否存在待生效的账号注销申请。
     *
     * <p>为 true 时账号仍可完成登录身份校验，但认证服务不得签发正常的访问与刷新令牌，
     * 只返回待注销状态和一次性撤回令牌。注销生效或撤回后该字段恢复为 false。</p>
     */
    private boolean deletionPending;

    /**
     * 注销生效时间，仅在 {@link #deletionPending} 为 true 时有值。
     *
     * <p>认证服务据此向客户端展示冷静期剩余时间，不参与令牌签发。</p>
     */
    private LocalDateTime deletionEffectiveAt;

    public UserBasicVO(Long userId, String userUuid, String userNo, String username, String roleCode) {
        // 新增注销字段后全参构造器参数增加，这里显式补全默认值，保持旧调用方兼容。
        this(userId, 0L, false, userUuid, userNo, username, roleCode, false, null);
    }

    public UserBasicVO(Long userId, String username, String roleCode) {
        this(userId, 0L, false, null, null, username, roleCode, false, null);
    }

    public UserBasicVO(Long userId, long version, String uuid, String userNo, String username, String role) {
        this(userId, version, false, uuid, userNo, username, role, false, null);
    }
}
