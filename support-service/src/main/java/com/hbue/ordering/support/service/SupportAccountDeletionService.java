package com.hbue.ordering.support.service;

import com.hbue.ordering.support.vo.AccountDeletionBlockersVO;

/**
 * 账号注销的工单侧阻断查询。
 *
 * <p>只读统计学生未关闭工单数量，不修改任何工单状态。调用方在每次资格校验和到期复核时都
 * 重新调用，保证用户在处理间隙新开工单时不会被误注销。</p>
 */
public interface SupportAccountDeletionService {

    /**
     * 查询学生未关闭工单数量。
     *
     * @param customerId 学生内部用户编号
     * @return 阻断统计
     */
    AccountDeletionBlockersVO blockers(Long customerId);
}
