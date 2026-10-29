package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 邮箱变更页的图片验证码校验请求。 */
public record VerifyEmailCaptchaRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 64, max = 64) String authorizationToken,
        @NotBlank @Size(max = 64) String captchaId,
        @NotBlank @Size(max = 8) String captchaCode) {
    @Override public String toString() { return "VerifyEmailCaptchaRequest[credentials=redacted]"; }
}
