package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 图片验证通过后，凭单次人机验证凭据向目标邮箱发送验证码。 */
public record SendEmailCodeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 64, max = 64) String authorizationToken,
        @NotBlank @Size(min = 64, max = 64) String captchaVerificationToken) {
    @Override public String toString() { return "SendEmailCodeRequest[redacted]"; }
}
