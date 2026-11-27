package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.dto.request.PasswordRecoveryCodeRequest;
import com.hbue.ordering.user.api.dto.PasswordRecoveryConfirmCommand;
import com.hbue.ordering.user.api.dto.PasswordRecoveryVerifyCommand;
import com.hbue.ordering.user.api.vo.PasswordRecoveryGrantVO;

/**
 * 找回密码的三步编排：发送验证码、校验验证码换取凭据、凭据重置密码。
 *
 * <p>三步之间的状态全部保存在 Redis 短期凭据中，认证服务不落地任何密码变更，最终写入由用户服务
 * 按一次性凭据完成。凭据只能用一次，过期或已使用后再提交必须失败。</p>
 *
 * <p>发送接口与登录、注册共用验证码频控，避免被当作短信/邮件轰炸通道。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface PasswordRecoveryService {

    /**
     * 为指定邮箱/账号发送找回密码验证码。
     *
     * @param body   账号标识与场景参数
     * @param source 请求来源标识，参与频控与日志排查
     */
    void send(PasswordRecoveryCodeRequest body, String source);

    /**
     * 校验验证码并换取一次性重置凭据。
     *
     * @param body   账号标识与验证码
     * @param source 请求来源标识
     * @return 供重置接口使用的一次性凭据
     */
    PasswordRecoveryGrantVO verify(PasswordRecoveryVerifyCommand body, String source);

    /**
     * 凭一次性凭据完成密码重置。
     *
     * @param body   重置凭据与新密码
     * @param source 请求来源标识
     */
    void confirm(PasswordRecoveryConfirmCommand body, String source);
}
