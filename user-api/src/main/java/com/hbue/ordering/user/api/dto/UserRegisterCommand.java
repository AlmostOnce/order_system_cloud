package com.hbue.ordering.user.api.dto;

import lombok.Data;

/**
 * 用户注册命令。
 *
 * <p>用于认证服务向 user-service 传递新用户注册资料，是服务间传输对象而非数据库对象。
 * {@code password} 是短暂传输的原始凭据，用户服务负责加密后保存；调用方不得记录该字段。</p>
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
     * 邮箱。
     */
    private String email;

    /**
     * 原始密码。
     *
     * <p>用户服务接收后必须进行 BCrypt 加密，
     * 禁止明文保存。</p>
     */
    private String password;
}
