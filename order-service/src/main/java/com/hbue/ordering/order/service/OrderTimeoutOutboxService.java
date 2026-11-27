package com.hbue.ordering.order.service;

import com.hbue.ordering.order.dto.OrderTimeoutEvent;

import java.util.List;

/**
 * 订单超时事件的本地 Outbox 读写。
 *
 * <p>超时事件必须与订单写入在同一本地事务内落库，否则会出现"订单已创建但永远不会超时关单"的
 * 悬挂订单，因此 {@link #record(OrderTimeoutEvent)} 以 MANDATORY 传播运行，调用方必须已有事务。</p>
 *
 * <p>发送状态由独立的发布任务按 pending → sent/failed/deadLetter 推进；重试次数耗尽后进入死信，
 * 保留原始错误摘要供人工排查，不删除记录。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
public interface OrderTimeoutOutboxService {

    /**
     * 在调用方事务内记录待发布的超时事件。
     *
     * @param event 超时事件
     */
    void record(OrderTimeoutEvent event);

    /**
     * 读取待发布事件，数量由 SQL 侧限制。
     *
     * @return 待发布事件列表
     */
    List<OrderTimeoutEvent> pending();

    /**
     * 原子领取一条待发事件的租约，供多实例部署时避免同一事件重复投递。
     *
     * <p>实现必须以"未发布且租约已到期"为条件更新 {@code next_attempt_at}；返回 true 的实例才负责
     * 本次发送，其余实例应直接跳过。租约到期后仍未被标记已发送（例如持有者崩溃）时允许重新领取，
     * 因此投递语义是至少一次，消费端必须按事件号幂等。</p>
     *
     * @param id      事件编号
     * @param seconds 租约秒数
     * @return true 表示本实例获得租约；false 表示已被其它实例领取或已发布
     */
    boolean lease(String id, int seconds);

    /**
     * 标记事件已被 Broker 确认接收。
     *
     * @param id 事件编号
     */
    void sent(String id);

    /**
     * 记录一次发送失败并安排退避重试。
     *
     * @param id    事件编号
     * @param error 失败原因摘要
     */
    void failed(String id, String error);

    /**
     * 把超过重试上限的事件转入死信状态，保留记录供人工处理。
     *
     * @param id 事件编号
     */
    void deadLetter(String id);
}
