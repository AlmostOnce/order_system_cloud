package com.hbue.ordering.user.service;

import com.hbue.ordering.user.api.dto.UserBalanceDeductCommand;
import com.hbue.ordering.user.api.vo.UserBalanceDeductResultVO;
import com.hbue.ordering.user.vo.UserBalanceVO;

/**
 * 用户余额业务服务。
 *
 * <p>余额作为支付渠道接入 payment-service，但扣款与记账留在 user-service：
 * 改 {@code users.balance} 与写 {@code balance_records} 在同一本地事务内完成，
 * 不跨服务做分布式事务（见架构文档 §6.4）。</p>
 *
 * @author order-system
 * @date 2026-10-14
 */
public interface UserBalanceService {

    /**
     * 查询本人余额与支付密码设置状态。
     *
     * @param userId 当前登录用户 ID
     * @return 余额视图对象
     * @throws com.hbue.ordering.common.core.exception.BusinessException 用户未登录或账户不可用时抛出
     */
    UserBalanceVO getMyBalance(Long userId);

    /**
     * 按业务单据扣减余额并幂等记账。
     *
     * <p>幂等由三重机制保证：调用前的流水存在性检查、{@code balance_records}
     * 唯一键冲突、以及条件扣款的受影响行数。同一业务单据重复提交只扣一次。</p>
     *
     * @param userId 用户 ID
     * @param command 余额扣款命令
     * @return 扣款结果
     * @throws com.hbue.ordering.common.core.exception.BusinessException 参数无效、支付密码错误、账户不可用或余额不足时抛出
     */
    UserBalanceDeductResultVO deduct(
            Long userId,
            UserBalanceDeductCommand command
    );
}
