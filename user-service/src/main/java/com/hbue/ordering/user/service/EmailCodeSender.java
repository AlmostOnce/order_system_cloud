package com.hbue.ordering.user.service;

/** 邮件验证码发送边界。 */
public interface EmailCodeSender {
    void send(String email, String code);
}
