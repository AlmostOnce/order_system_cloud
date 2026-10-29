package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 仅用于本人邮箱换绑；凭据仅在请求期间传递且不得写入日志。 */
public record ChangeOwnEmailRequest(
        @NotBlank(message = "新邮箱不能为空") @Email(message = "邮箱格式不正确")
        @Size(max = 254, message = "邮箱长度不能超过254个字符") String email,
        @NotBlank @Size(min = 64, max = 64) String authorizationToken,
        @NotBlank(message = "请输入邮箱验证码") @Size(min = 6, max = 6, message = "邮箱验证码必须为六位") String emailCode) {
    @Override
    public String toString() { return "ChangeOwnEmailRequest[credentials=redacted]"; }
}
