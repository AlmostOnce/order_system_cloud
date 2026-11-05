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
    public void send(String email, String code) { sendMessage(email, code, "邮箱验证"); }

    @Override public void sendPasswordReset(String email, String code) { sendMessage(email, code, "找回登录密码"); }

    @Override public void sendPayPasswordReset(String email, String code) { sendMessage(email, code, "找回支付密码"); }

    @Override public void sendOwnershipVerification(String email,String code) {sendMessage(email,code,"验证邮箱所有权");}

    private void sendMessage(String email, String code, String purpose) {
        if (username.isBlank() || password.isBlank()) {
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "邮件服务尚未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(username);
        message.setTo(email);
        message.setSubject("校园食堂" + purpose);
        message.setText(("您正在" + purpose + "，验证码是：") + code + "。验证码10分钟内有效，请勿向他人透露。如非本人操作，请忽略此邮件。");
        try {
            mail.send(message);
        } catch (MailException exception) {
            // 不记录 SMTP 异常内容，避免泄露邮箱地址或凭据。
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试");
        }
    }
}
