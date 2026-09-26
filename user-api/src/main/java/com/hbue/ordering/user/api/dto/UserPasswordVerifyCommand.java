package com.hbue.ordering.user.api.dto;

import lombok.Data;

/**
 * 用户密码校验命令。
 *
 * <p>用于认证服务请求用户服务校验账号密码。</p>
 *
 * <p>密码只允许在内存和 HTTPS/内部安全链路中短暂传递，
 * 禁止写入日志。</p>
 *
 * @author order-system
 * @date 2026-09-20
 */
@Data
public class UserPasswordVerifyCommand {

    /**
     * 用户名。
     */
    private String username;

    /**
     * 用户明文密码。
     */
    private String password;
}