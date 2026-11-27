package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 撤回账号注销申请。
 *
 * <p>提交注销后账号已失去正常登录令牌，因此撤回接口不要求访问令牌，只接受登录时
 * 签发的一次性撤回令牌。服务端按令牌摘要反查用户，令牌只保存在 Redis 中且有效期为
 * 10 分钟，不能由客户端指定用户 ID。</p>
 *
 * @param cancelToken 待注销账号登录时获得的一次性撤回令牌
 * @author order-system
 * @date 2026-11-24
 */
public record AccountDeletionCancelRequest(
        @NotBlank(message = "撤回令牌不能为空")
        @Size(min = 64, max = 64, message = "撤回令牌格式错误")
        String cancelToken) {
}
