package com.hbue.ordering.user.vo;

/** 图片验证码验证成功后签发的单次发送凭据。 */
public record EmailCaptchaVerificationVO(String captchaVerificationToken, long expiresInSeconds) { }
