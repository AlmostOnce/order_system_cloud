package com.hbue.ordering.notification.service;

import com.hbue.ordering.notification.vo.NotificationConversationVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统通知会话业务契约。
 *
 * <p>系统会话是持久化实体而不是查询时临时拼装：通知写入时推进摘要，消息页只读取会话表。
 * 订单中心由服务端在首次访问时创建，保证会话一旦出现便常驻，已读也不会消失。</p>
 *
 * @author order-system
 * @date 2026-11-26
 */
public interface SystemConversationService {

    /**
     * 推进系统会话的最新摘要与时间。
     *
     * <p>在通知落库成功后调用，按 (用户, 会话类型) 幂等覆盖；重复执行不会产生重复会话。
     * 事务回滚时摘要一起回滚，不能出现摘要指向未提交通知的情况。</p>
     *
     * @param customerId       用户内部编号
     * @param conversationType 会话类型
     * @param title            会话名称
     * @param preview          最新通知摘要
     * @param latestAt         最新通知时间
     */
    void advance(long customerId, String conversationType, String title, String preview, LocalDateTime latestAt);

    /**
     * 查询用户的系统会话。
     *
     * <p>首次调用会创建订单中心会话并回填历史通知摘要，因此会写库；
     * 写操作是幂等的，重复调用不会改变结果。</p>
     *
     * @param customerId 用户内部编号
     * @return 会话列表，订单中心始终存在
     */
    List<NotificationConversationVO> list(long customerId);
}
