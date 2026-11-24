package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.vo.AccountDeletionCancelTokenVO;
import com.hbue.ordering.user.dto.request.AccountDeletionAuthorizationRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionCancelRequest;
import com.hbue.ordering.user.dto.request.AccountDeletionSubmitRequest;
import com.hbue.ordering.user.vo.AccountDeletionAuthorizationVO;
import com.hbue.ordering.user.vo.AccountDeletionEligibilityVO;
import com.hbue.ordering.user.vo.AccountDeletionSubmitVO;

import java.time.Instant;

/**
 * 学生账号注销业务边界。
 *
 * <p>注销采用“资格核对 → 邮箱验证 → 提交申请 → 72 小时冷静期 → 到期复核匿名化”的状态机。
 * 提交后账号立即失去正常会话，只能通过登录时签发的一次性撤回令牌在冷静期内撤回。
 * 所有方法只接受已认证的本人用户 ID，不接受客户端指定他人。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
public interface AccountDeletionService {

    /**
     * 查询当前账号的注销资格与阻断项。
     *
     * @param userId 当前登录用户 ID
     * @return 资格视图；依赖无法确认时返回服务不可用
     */
    AccountDeletionEligibilityVO eligibility(Long userId);

    /**
     * 向当前绑定邮箱发送注销验证码。
     *
     * @param userId 当前登录用户 ID
     */
    void sendVerificationCode(Long userId);

    /**
     * 校验注销验证码并签发一次性授权令牌。
     *
     * @param userId  当前登录用户 ID
     * @param request 验证码
     * @return 一次性授权令牌
     */
    AccountDeletionAuthorizationVO authorize(Long userId, AccountDeletionAuthorizationRequest request);

    /**
     * 提交注销申请。
     *
     * <p>事务内重新核对阻断项、余额和邮箱验证状态，成功后递增凭据版本使刷新令牌失效，
     * 并把当前访问令牌加入黑名单，保证提交设备立即下线。重复提交若申请仍待生效，
     * 返回原生效时间且不重置冷静期。</p>
     *
     * @param userId               当前登录用户 ID
     * @param accessTokenId        当前访问令牌的 jti，用于立即加入黑名单
     * @param accessTokenExpiresAt 当前访问令牌过期时间，用于确定黑名单 TTL
     * @param request              授权令牌与余额放弃确认
     * @return 注销生效时间
     */
    AccountDeletionSubmitVO submit(Long userId,
                                   String accessTokenId,
                                   Instant accessTokenExpiresAt,
                                   AccountDeletionSubmitRequest request);

    /**
     * 使用一次性撤回令牌撤回注销申请。
     *
     * <p>该接口不要求登录令牌，调用方只能凭登录时签发的撤回令牌操作本人账号。</p>
     *
     * @param request 撤回令牌
     */
    void cancel(AccountDeletionCancelRequest request);

    /**
     * 为待注销账号签发一次性撤回令牌。
     *
     * <p>仅供认证服务在登录密码校验通过后调用；没有待生效申请时拒绝签发。
     * 冷静期已过但到期扫描尚未处理时返回空令牌，让客户端展示等待状态而不是拒绝登录。</p>
     *
     * @param userId 用户 ID
     * @return 撤回令牌及有效秒数；不可撤回时令牌子段为空
     */
    AccountDeletionCancelTokenVO issueCancelToken(Long userId);

    /**
     * 处理一个已到期的注销账号。
     *
     * <p>由扫描任务在租约保护下逐用户调用。事务内重新核对订单、退款、工单和余额：
     * 仍有阻断项时撤销注销并邮件通知；否则清零余额、写 WRITE_OFF 流水并匿名化账号。</p>
     *
     * @param userId 用户 ID
     */
    void finalizeDueUser(Long userId);

    /**
     * 发送注销生效前提醒。
     *
     * <p>通过条件更新抢占提醒权，同一申请只发送一次；邮件失败只记录日志，不改变注销状态。</p>
     *
     * @param userId 用户 ID
     */
    void sendEffectiveReminder(Long userId);
}
