package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;
import com.hbue.ordering.user.vo.PayPasswordRecoveryAuthorizationVO;

/**
 * 支付密码重置的四步编排：登录密码授权、发送验证码、校验验证码、写入新支付密码。
 *
 * <p>支付密码关系到余额扣款，因此比登录密码找回多一道前置门槛：必须先验证登录密码才能拿到
 * 授权凭据，后续每一步都要同时携带用户编号与授权凭据，缺一不可。</p>
 *
 * <p>授权凭据与用户编号绑定：拿到他人凭据也无法用于自己的账号，反之亦然。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface PayPasswordRecoveryService {

    /**
     * 用登录密码换取支付密码重置授权。
     *
     * @param userId        当前登录用户编号
     * @param loginPassword 登录密码明文，由实现校验
     * @return 重置授权凭据
     */
    PayPasswordRecoveryAuthorizationVO authorize(Long userId, String loginPassword);

    /**
     * 向账号绑定邮箱发送支付密码重置验证码。
     *
     * @param userId     当前登录用户编号
     * @param token      重置授权凭据
     * @param captchaId  图片验证码标识
     * @param captchaCode 图片验证码内容
     */
    void send(Long userId, String token, String captchaId, String captchaCode);

    /**
     * 校验邮箱验证码并签发一次性重置凭据。
     *
     * @param userId 当前登录用户编号
     * @param token  重置授权凭据
     * @param code   邮箱验证码
     * @return 支付密码重置凭据
     */
    PasswordRecoveryGrantVO verify(Long userId, String token, String code);

    /**
     * 凭重置凭据写入新的支付密码。
     *
     * @param userId   当前登录用户编号
     * @param token    支付密码重置凭据
     * @param password 新支付密码明文
     */
    void confirm(Long userId, String token, String password);
}
