package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交账号注销的请求。
 *
 * <p>必须携带邮箱验证通过后换取的一次性授权令牌；令牌与用户绑定且只能使用一次。
 * 当账号仍有余额时，{@code forfeitBalance} 必须为 true，表示用户已在前端确认放弃
 * 余额（上限由服务端配置校验）。客户端不提交余额数值，余额一律以服务端读取结果为准。</p>
 *
 * @param authorizationToken 邮箱验证通过后签发的一次性注销授权令牌
 * @param forfeitBalance     是否已确认放弃当前余额；无余额时可为空
 * @author order-system
 * @date 2026-11-24
 */
public record AccountDeletionSubmitRequest(
        @NotBlank(message = "注销授权令牌不能为空")
        @Size(min = 64, max = 64, message = "注销授权令牌格式错误")
        String authorizationToken,
        Boolean forfeitBalance) {
}
