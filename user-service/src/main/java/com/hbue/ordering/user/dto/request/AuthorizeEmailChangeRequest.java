package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用于打开邮箱变更流程的登录密码验证请求。 */
public record AuthorizeEmailChangeRequest(
        @NotBlank(message = "请输入登录密码") @Size(max = 64, message = "登录密码长度不能超过64个字符") String loginPassword) {
    @Override public String toString() { return "AuthorizeEmailChangeRequest[credentials=redacted]"; }
}
