package com.hbue.ordering.user.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 当前登录学生主动修改登录密码的请求。
 *
 * <p>只接收当前密码与新密码，不接受用户编号。用户编号始终取自 JWT 的 sub，
 * 避免客户端替换目标账号。当前密码只用于本次校验，不落库、不写日志；
 * 新密码长度在服务层按字符数与 UTF-8 字节数双重校验，与注册、找回密码口径一致。</p>
 *
 * @param currentPassword 当前登录密码，不能为空
 * @param newPassword     新登录密码，6 至 64 个字符且 UTF-8 不超过 72 字节
 * @author order-system
 * @date 2026-11-23
 */
public record ChangeOwnPasswordRequest(
        @Size(max = 64, message = "当前登录密码长度不能超过64个字符") String currentPassword,
        @Size(min = 6, max = 64, message = "新登录密码长度为6至64个字符") String newPassword) {
}
