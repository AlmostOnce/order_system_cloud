package com.hbue.ordering.user.api.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户基础信息视图对象。
 *
 * <p>该对象用于用户服务和其他业务服务之间传输用户基础信息。</p>
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
     * 用户名。
     */
    private String username;

    /**
     * 角色编码。
     */
    private String roleCode;
}