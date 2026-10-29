package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.SendEmailCodeRequest;
import com.hbue.ordering.user.dto.request.VerifyEmailCaptchaRequest;

/** 本人邮箱验证码发送及验证。 */
public interface EmailVerification {
    String authorize(Long userId, String loginPassword);
    String verifyCaptcha(Long userId, VerifyEmailCaptchaRequest request);
    void send(Long userId, SendEmailCodeRequest request);
    void consume(Long userId, String authorizationToken, String email, String code);
    void verifyAuthorization(Long userId, String authorizationToken);
    void consumeAuthorization(Long userId, String authorizationToken);
}
