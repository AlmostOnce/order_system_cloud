package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;

/**
 * 用户服务侧的找回密码权威实现。
 *
 * <p>负责邮箱存在性校验、验证码校验、重置凭据签发与一次性消费，以及最终密码写入。
 * 认证服务的同名接口只做代理与频控，密码规则、凭据有效期等业务约束都以本接口为准。</p>
 *
 * <p>重置凭据一次性使用：提交成功后立即失效；提交失败（如新密码不符合规则）不会消耗凭据，
 * 允许用户在同一凭据有效期内重新提交。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface PasswordRecoveryService {

    /**
     * 向账号绑定邮箱发送找回密码验证码。
     *
     * @param email 邮箱
     */
    void send(String email);

    /**
     * 校验验证码并签发一次性重置凭据。
     *
     * @param email 邮箱
     * @param code  邮箱验证码
     * @return 重置凭据
     */
    PasswordRecoveryGrantVO verify(String email, String code);

    /**
     * 凭重置凭据写入新密码。
     *
     * @param resetToken  一次性重置凭据
     * @param newPassword 新密码明文，由实现负责加密与规则校验
     */
    void confirm(String resetToken, String newPassword);
}
