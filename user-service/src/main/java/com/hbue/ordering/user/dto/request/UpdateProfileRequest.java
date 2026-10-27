package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 仅允许更新本人昵称和绑定邮箱；登录密码仅用于换绑确认。 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空") @Size(min = 2, max = 64, message = "昵称长度为2至64个字符") String username,
        @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱长度不能超过254个字符") String email,
        @Size(max = 64, message = "登录密码长度不能超过64个字符") String loginPassword) {
    @Override
    public String toString() { return "UpdateProfileRequest[credentials=redacted]"; }
}
