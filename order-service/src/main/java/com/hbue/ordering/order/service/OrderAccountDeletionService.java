package com.hbue.ordering.order.service;

import com.hbue.ordering.order.vo.AccountDeletionBlockersVO;

/**
 * 账号注销的订单侧阻断查询。
 *
 * <p>只读统计未完成订单与处理中退款数量，供用户服务在资格校验和到期复核时调用；
 * 本服务不修改任何订单或退款状态。</p>
 */
public interface OrderAccountDeletionService {

    /**
     * 查询学生在订单侧是否存在阻断注销的数据。
     *
     * @param customerId 学生内部用户编号
     * @return 阻断统计
     */
    AccountDeletionBlockersVO blockers(Long customerId);
}
