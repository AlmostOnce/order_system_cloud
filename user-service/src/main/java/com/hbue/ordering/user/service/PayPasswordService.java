package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.PayPasswordSetRequest;

/**
 * 支付密码业务服务。
 *
 * <p>支付密码独立于登录密码（见架构文档 §6.4）：账号被盗时，登录态被窃取
 * 不应直接导致余额被花掉，余额支付需要第二道凭据。校验失败需要计数与锁定，
 * 防止暴力破解。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public interface PayPasswordService {

    /**
     * 首次设置或修改支付密码。
     *
     * <p>尚未设置时用登录密码做二次确认；已设置时必须提供并校验原支付密码。
     * 支付密码不得与登录密码或原支付密码相同。</p>
     *
     * @param userId 用户 ID
     * @param request 支付密码设置请求
     */
    void setPayPassword(Long userId, PayPasswordSetRequest request);

    /**
     * 校验支付密码，失败时累计计数并在超过阈值后锁定。
     *
     * @param userId 用户 ID
     * @param payPassword 支付密码明文
     */
    void verifyPayPassword(Long userId, String payPassword);

    /**
     * 查询用户是否已设置支付密码。
     *
     * @param userId 用户 ID
     * @return 已设置时返回 true
     */
    boolean isPayPasswordSet(Long userId);
}
