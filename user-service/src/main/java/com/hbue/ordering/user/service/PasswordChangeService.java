package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.ChangeOwnPasswordRequest;

/**
 * 已登录用户主动修改登录密码的业务契约。
 *
 * <p>与找回密码不同，本接口不依赖邮箱验证码，而是在已登录会话内要求调用方
 * 提交当前密码作为身份再确认。用户编号只能由接口层从 JWT 解析后传入，
 * 业务层不接受请求体指定目标账号。</p>
 *
 * <p>修改成功后必须递增凭据版本，使旧访问令牌与刷新令牌全部失效；失败次数
 * 通过 Redis 固定窗口计数限制，避免持有被盗会话的攻击者在线爆破当前密码。</p>
 *
 * @author order-system
 * @date 2026-11-23
 */
public interface PasswordChangeService {

    /**
     * 校验当前密码后修改本人登录密码。
     *
     * @param userId  当前登录用户编号，来自 JWT 的 sub，不能为空
     * @param request 当前密码与新密码
     * @throws com.hbue.ordering.common.core.exception.BusinessException
     *         未登录、参数不合法、当前密码错误、失败次数超限、账号不可用或并发改密冲突时抛出
     */
    void changeOwnPassword(Long userId, ChangeOwnPasswordRequest request);
}
