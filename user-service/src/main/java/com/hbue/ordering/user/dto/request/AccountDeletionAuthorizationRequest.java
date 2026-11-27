package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 账号注销邮箱验证码校验请求。
 *
 * <p>验证码只会发送到当前账号已绑定并验证的邮箱。用户编号取自 JWT 的 sub，
 * 请求体不接受目标用户；验证码只用于本次校验，服务端不保存明文。</p>
 *
 * @param code 6 位数字验证码
 * @author order-system
 * @date 2026-11-24
 */
public record AccountDeletionAuthorizationRequest(
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "\\d{6}", message = "验证码为6位数字")
        String code) {
}
