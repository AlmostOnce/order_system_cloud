package com.hbue.ordering.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 用户安全配置。
 *
 * <p>当前只提供 BCrypt 密码加密器，
 * 不负责登录认证和接口权限控制。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Configuration
public class UserSecurityConfig {

    /**
     * 创建密码加密器。
     *
     * @return BCrypt 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用 BCrypt 保存密码，禁止保存明文密码。
        return new BCryptPasswordEncoder();
    }
}