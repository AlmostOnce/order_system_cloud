package com.hbue.ordering.auth.service;

import com.hbue.ordering.auth.dto.request.EmailOwnershipSendRequest;
import com.hbue.ordering.user.api.dto.EmailEnrollmentConfirmCommand;
import com.hbue.ordering.user.api.dto.RegistrationEmailVerifyCommand;
import com.hbue.ordering.user.api.vo.EmailEnrollmentChallengeVO;
import com.hbue.ordering.user.api.vo.EmailRegistrationGrantVO;
import com.hbue.ordering.user.api.vo.UserBasicVO;

/**
 * 邮箱归属的注册校验与账号绑定编排。
 *
 * <p>认证服务不保存邮箱，只负责"发验证码 → 校验验证码 → 换取一次性凭据"的流程；真正的邮箱写入
 * 由用户服务完成。因此本接口所有方法都以验证码和来源标识为输入，不接收客户端自报的身份。</p>
 *
 * <p>验证码校验与频控复用 {@code CaptchaService}，防止通过本接口绕过注册或找回密码的限流。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface EmailOwnershipService {

    /**
     * 为注册流程发送绑定邮箱验证码。
     *
     * @param body   邮箱与业务场景参数
     * @param source 请求来源标识，参与频控与日志排查
     */
    void sendRegistration(EmailOwnershipSendRequest body, String source);

    /**
     * 校验注册验证码并换取注册邮箱凭据。
     *
     * @param body   邮箱与验证码
     * @param source 请求来源标识
     * @return 供随后注册请求使用的一次性凭据
     */
    EmailRegistrationGrantVO verifyRegistration(RegistrationEmailVerifyCommand body, String source);

    /**
     * 为已通过密码校验的用户生成邮箱绑定挑战。
     *
     * @param verifiedPasswordUser 已通过密码校验的用户基础信息
     * @return 绑定挑战，包含后续发送与确认所需的一次性标识
     */
    EmailEnrollmentChallengeVO challenge(UserBasicVO verifiedPasswordUser);

    /**
     * 为账号绑定流程发送验证码。
     *
     * @param body   邮箱与绑定挑战参数
     * @param source 请求来源标识
     */
    void sendEnrollment(EmailOwnershipSendRequest body, String source);

    /**
     * 校验绑定验证码并由用户服务写入邮箱归属。
     *
     * @param body   绑定挑战与验证码
     * @param source 请求来源标识
     */
    void confirmEnrollment(EmailEnrollmentConfirmCommand body, String source);
}
