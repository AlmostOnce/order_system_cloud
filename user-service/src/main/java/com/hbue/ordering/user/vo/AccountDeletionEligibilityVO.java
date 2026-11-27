package com.hbue.ordering.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号注销资格视图。
 *
 * <p>一次性返回前端做决定所需的全部信息：当前是否已有待生效申请、服务端余额与
 * 可放弃上限、是否存在未验证邮箱，以及订单、退款、工单等阻断项。
 * 前端只做展示与二次确认，最终提交仍由服务端在事务内重新核对全部条件。
 * 不返回邮箱明文和密码等敏感字段。</p>
 *
 * @author order-system
 * @date 2026-11-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionEligibilityVO {

    /**
     * 是否已有待生效的注销申请。
     */
    private boolean pending;

    /**
     * 本次申请的提交时间，仅 pending 时有值。
     */
    private LocalDateTime requestedAt;

    /**
     * 本次注销的生效时间，仅 pending 时有值。
     */
    private LocalDateTime effectiveAt;

    /**
     * 当前余额，由服务端读取，客户端不可篡改。
     */
    private BigDecimal balance;

    /**
     * 允许放弃的余额上限；超过该值必须消费或走人工反馈。
     */
    private BigDecimal maxForfeitableBalance;

    /**
     * 当前余额是否超过可放弃上限。
     */
    private boolean balanceOverLimit;

    /**
     * 是否存在被拒绝且可能仍可申诉的退款。
     *
     * <p>被拒退款不阻断注销，但注销后账号不可再用，需要前端提示用户先决定是否申诉。</p>
     */
    private boolean rejectedRefundWarning;

    /**
     * 脱敏后的绑定邮箱；未绑定邮箱时为空。
     */
    private String maskedEmail;

    /**
     * 当前邮箱是否已完成所有权验证。
     */
    private boolean emailVerified;

    /**
     * 阻断项列表；为空且余额未超限时 canApply 为 true。
     */
    private List<AccountDeletionBlockerVO> blockers;

    /**
     * 是否满足提交注销的前置条件。
     */
    private boolean canApply;
}
