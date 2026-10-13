package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改用户角色编码的请求。
 *
 * <p>角色是技术身份，仅 {@code SYSTEM_ADMIN} 可分配；
 * 该请求只写 {@code users.role_code}，不涉及数据范围。</p>
 *
 * @author order-system
 * @date 2026-10-13
 */
@Data
public class UserRoleUpdateRequest {

    /**
     * 目标角色编码，取值必须是 {@code RoleCode} 中已定义的编码。
     */
    @NotBlank(message = "角色编码不能为空")
    private String roleCode;
}
