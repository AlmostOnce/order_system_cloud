package com.hbue.ordering.user.api.dto;

import lombok.Data;

/**
 * 用户注册命令。
 *
 * <p>用于认证服务向用户服务传递注册信息。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
public class UserRegisterCommand {

    /**
     * 用户名。
     */
    private String username;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 原始密码。
     *
     * <p>用户服务接收后必须进行 BCrypt 加密，
     * 禁止明文保存。</p>
     */
    private String password;
}