package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求对象。
 *
 * <p>该对象只负责接收注册参数，
 * 不直接使用用户数据库实体。</p>
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
     * 手机号。
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(
            regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确"
    )
    private String phone;

    /**
     * 登录密码。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6 到 64 个字符之间")
    private String password;
}