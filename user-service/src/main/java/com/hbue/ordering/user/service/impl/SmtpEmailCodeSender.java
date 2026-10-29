package com.hbue.ordering.user.service.impl;

import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.user.service.EmailCodeSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** 通过已配置的 QQ 邮箱 SMTP 发送验证码。 */
@Service
public class SmtpEmailCodeSender implements EmailCodeSender {
    private final JavaMailSender mail;
    private final String username;
    private final String password;

    public SmtpEmailCodeSender(JavaMailSender mail, @Value("${spring.mail.username:}") String username,
                               @Value("${spring.mail.password:}") String password) {
        this.mail = mail;
        this.username = username;
        this.password = password;
    }

    @Override
    public void send(String email, String code) {
        if (username.isBlank() || password.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮件服务尚未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject("校园食堂邮箱验证码");
        message.setText("您的邮箱验证码是：" + code + "。验证码 10 分钟内有效，请勿向他人透露。");
        try {
            mail.send(message);
        } catch (MailException exception) {
            // 不记录 SMTP 异常内容，避免泄露邮箱地址或凭据。
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试");
        }
    }
}
