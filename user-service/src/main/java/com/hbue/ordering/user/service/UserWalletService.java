package com.hbue.ordering.user.service;

import com.hbue.ordering.user.dto.request.RechargeRequest;
import com.hbue.ordering.user.vo.BalanceRecordPageVO;
import com.hbue.ordering.user.vo.BalanceRecordVO;
import com.hbue.ordering.user.vo.UserProfileVO;

/**
 * 学生钱包的充值、流水与个人中心视图。
 *
 * <p>充值以客户端幂等键为准：同键同金额重复提交返回首次结果，同键不同金额返回冲突，
 * 保证网络重试不会重复加钱。余额变更与流水写入在同一本地事务内完成，不存在"加了余额却没有流水"。</p>
 *
 * <p>流水使用 id 倒序游标分页，避免深分页随数据增长而变慢；类型筛选采用白名单，
 * 非法值直接拒绝而不是退化为全部流水。</p>
 *
 * @author order-system
 * @date 2026-11-20
 */
public interface UserWalletService {

    /**
     * 充值并返回本次流水。
     *
     * @param userId         当前登录用户编号
     * @param idempotencyKey 客户端幂等键
     * @param request        充值金额等参数
     * @return 充值流水
     */
    BalanceRecordVO recharge(Long userId, String idempotencyKey, RechargeRequest request);

    /**
     * 分页查询钱包流水。
     *
     * @param userId   当前登录用户编号
     * @param type     流水类型筛选，可空
     * @param beforeId 游标：只返回编号小于该值的记录，可空
     * @param limit    每页条数
     * @return 流水分页结果
     */
    BalanceRecordPageVO listRecords(Long userId, String type, Long beforeId, int limit);

    /**
     * 查询当前用户的个人中心视图，含余额等派生信息。
     *
     * @param userId 当前登录用户编号
     * @return 个人中心视图
     */
    UserProfileVO getMyProfile(Long userId);
}
