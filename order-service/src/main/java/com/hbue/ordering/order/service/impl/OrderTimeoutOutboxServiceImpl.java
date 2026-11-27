package com.hbue.ordering.order.service.impl;

import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import com.hbue.ordering.order.mapper.OrderTimeoutOutboxMapper;
import com.hbue.ordering.order.model.OrderTimeoutOutboxDO;
import com.hbue.ordering.order.service.OrderTimeoutOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单超时事件 Outbox 的实现。
 *
 * <p>写入必须依附调用方事务（{@link Propagation#MANDATORY}），这样订单创建与超时事件落库同生共死；
 * 若被单独调用会直接抛出 {@code IllegalTransactionStateException}，属于有意的失败设计，
 * 而不是遗漏了事务注解。</p>
 *
 * <p>状态推进方法只做一次条件更新，不校验受影响行数：发布任务在下一轮会重新读到未推进成功的记录，
 * 重复推进是幂等的，因此不需要额外补偿。</p>
 *
 * @author order-system
 * @date 2026-10-18
 */
@Service
public class OrderTimeoutOutboxServiceImpl implements OrderTimeoutOutboxService {

    /**
     * 超时事件存储。
     */
    private final OrderTimeoutOutboxMapper mapper;

    /**
     * 创建 Outbox 服务。
     *
     * @param mapper 超时事件存储
     */
    public OrderTimeoutOutboxServiceImpl(OrderTimeoutOutboxMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 在调用方事务内写入一条待发布的超时事件。
     *
     * @param event 超时事件
     * @throws IllegalArgumentException 事件字段不完整
     * @throws IllegalStateException    未写入任何行，说明唯一键冲突或表结构异常
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OrderTimeoutEvent event) {
        if (!event.valid()) throw new IllegalArgumentException("无效超时事件");
        var row = new OrderTimeoutOutboxDO();
        row.setEventId(event.eventId());
        row.setOrderId(event.orderId());
        row.setExpiresAt(event.expiresAt());
        if (mapper.insert(row) != 1) throw new IllegalStateException("超时事件未持久化");
    }

    /**
     * 读取待发布事件并转换为消息载荷。
     */
    @Override
    public List<OrderTimeoutEvent> pending() {
        return mapper.pending().stream().map(row -> new OrderTimeoutEvent(row.getEventId(), row.getOrderId(), row.getExpiresAt())).toList();
    }

    /**
     * 尝试占用一条待发事件的租约。
     *
     * <p>租约由条件更新实现：只有把 {@code next_attempt_at} 从"已到期"推进到未来的调用方得到 1，
     * 并发实例会得到 0 并跳过发送。租约到期前其它实例不会重发，到期后若仍未标记已发送则可被重新领取。</p>
     */
    @Override
    public boolean lease(String id, int seconds) {
        return mapper.lease(id, seconds) == 1;
    }

    /**
     * 标记事件已发送。
     */
    @Override
    public void sent(String id) {
        mapper.sent(id);
    }

    /**
     * 记录发送失败并退避。
     */
    @Override
    public void failed(String id, String error) {
        mapper.failed(id, error);
    }

    /**
     * 转入死信，保留记录供人工处理。
     */
    @Override
    public void deadLetter(String id) {
        mapper.deadLetter(id);
    }
}
