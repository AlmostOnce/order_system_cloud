package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求对象。
 *
 * <p>该对象只负责接收注册参数，
 * 不直接使用用户数据库实体。字段格式通过 Jakarta Validation 约束；密码
 * 只用于注册命令传递，后续由 user-service 加密保存，认证服务不持久化密码。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
public class RegisterRequest {

    /**
     * 用户名。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 64, message = "用户名长度必须在 2 到 64 个字符之间")
    private String username;

    /**
     * 邮箱。
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过 254 个字符")
    private String email;

    /**
     * 登录密码。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6 到 64 个字符之间")
    private String password;
}
