package com.hbue.ordering.user.api.dto;

import lombok.Data;

/**
 * 用户密码校验命令。
 *
 * <p>用于认证服务请求 user-service 校验账号密码，是认证链路中的内部传输对象，
 * 不代表密码可在服务端明文存储。用户服务应使用密码哈希校验，调用方和被调用方均不得记录密码。</p>
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
