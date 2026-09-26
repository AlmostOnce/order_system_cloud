package com.hbue.ordering.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录请求对象。
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    /**
     * 登录密码。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64 个字符")
    private String password;
}