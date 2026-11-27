package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.vo.EmailEnrollmentChallengeVO;
import com.hbue.ordering.user.api.vo.EmailRegistrationGrantVO;

/**
 * 用户服务侧的邮箱归属权威实现。
 *
 * <p>认证服务只负责验证码发放与频控，真正的"邮箱是否已被占用、验证码是否正确、凭据是否可用"
 * 全部由本接口在用户库上判定，保证唯一性约束只有一处来源。</p>
 *
 * <p>凭据一次性使用：注册凭据在注册成功后立即消费，绑定凭据与用户当前 {@code credentialVersion}
 * 绑定，密码变更后旧凭据自动失效，避免被盗凭据在改密后继续生效。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface EmailOwnershipService {

    /**
     * 发送注册验证码，邮箱已被占用时直接拒绝。
     *
     * @param email 待绑定邮箱
     */
    void sendRegistration(String email);

    /**
     * 校验注册验证码并签发注册凭据。
     *
     * @param email 待绑定邮箱
     * @param code  邮箱验证码
     * @return 一次性注册凭据
     */
    EmailRegistrationGrantVO verifyRegistration(String email, String code);

    /**
     * 消费注册凭据，标记该凭据已使用。
     *
     * @param email 已校验邮箱
     * @param token 注册凭据
     */
    void consumeRegistration(String email, String token);

    /**
     * 为已登录用户签发邮箱绑定挑战，绑定当前凭据版本。
     *
     * @param userId            用户编号
     * @param credentialVersion 当前凭据版本，密码变更后旧挑战失效
     * @return 绑定挑战
     */
    EmailEnrollmentChallengeVO challenge(Long userId, long credentialVersion);

    /**
     * 向绑定挑战登记的邮箱发送验证码。
     *
     * @param token 绑定挑战凭据
     * @param email 待绑定邮箱
     */
    void sendEnrollment(String token, String email);

    /**
     * 校验绑定验证码并写入邮箱归属。
     *
     * @param token 绑定挑战凭据
     * @param email 待绑定邮箱
     * @param code  邮箱验证码
     */
    void confirmEnrollment(String token, String email, String code);
}
