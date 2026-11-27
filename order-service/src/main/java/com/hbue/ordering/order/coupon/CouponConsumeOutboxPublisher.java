package com.hbue.ordering.order.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.order.mapper.CouponConsumeOutboxMapper;
import com.hbue.ordering.order.model.CouponConsumeOutboxRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 将支付成功后的优惠券核销 Outbox 异步发布至 RocketMQ。
 *
 * <p>发布器只依赖本地 Outbox、JSON 解码器和同步确认的消息桥，不参与支付事务；支付事务只负责
 * 把事件写进 Outbox 表，Broker 的可用性不会拖慢也不会回滚已经完成的支付。</p>
 *
 * <p>消息可能在 Broker 已接收但数据库尚未标记时重发，因此券服务必须按事件号/订单号幂等核销。</p>
 *
 * <p>本类与 {@link CouponConsumeOutboxWriter}、{@link OrderCouponConsumeEvent} 同属 coupon 特性包，
 * 与 ranking/notification/recommendation 下的发布器保持一致的包组织方式。</p>
 *
 * @author order-system
 * @date 2026-10-24
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "coupon.consume.messaging.enabled", havingValue = "true")
public class CouponConsumeOutboxPublisher {

    /**
     * 当前类日志对象。
     */
    private static final Logger log = LoggerFactory.getLogger(CouponConsumeOutboxPublisher.class);

    /**
     * 单条事件的发送租约（秒）。多实例同时扫描到同一行时，只有成功把 {@code next_attempt_at}
     * 推进到未来的实例负责发送；租约需大于一次同步发送的耗时，到期后仍未被确认才允许重新领取。
     */
    private static final int LEASE_SECONDS = 30;

    /**
     * 读取待发记录并在同步发送后标记状态或安排退避重试。
     */
    private final CouponConsumeOutboxMapper outbox;

    /**
     * 验证数据库中已持久化的 JSON 快照。
     */
    private final ObjectMapper json;

    /**
     * 向配置好的优惠券核销 RocketMQ binding 发布事件。
     */
    private final StreamBridge stream;

    /**
     * 发布器仅依赖本地 Outbox、JSON 解码器和同步确认的消息桥。
     *
     * @param outbox 持久化待发消息并记录发布/重试状态的 Mapper
     * @param json   解码 Outbox 载荷并核对快照字段的工具
     * @param stream 向优惠券消息 binding 发送消息的桥接器
     */
    public CouponConsumeOutboxPublisher(CouponConsumeOutboxMapper outbox, ObjectMapper json, StreamBridge stream) {
        this.outbox = outbox;
        this.json = json;
        this.stream = stream;
    }

    /**
     * 有界扫描待发记录；Broker 未确认或响应丢失时退避重试，不能影响已完成的支付确认事务。
     *
     * <p>单条失败只退避该条记录，不断开整轮扫描；退避写入本身失败时仅记录错误，
     * 让下一轮调度重新读取同一行，避免把异常抛给调度线程导致后续任务全部停摆。</p>
     */
    @Scheduled(fixedDelayString = "${coupon.consume.messaging.publish-delay-ms:1000}")
    public void publishPending() {
        try {
            for (CouponConsumeOutboxRow row : outbox.pending()) {
                try {
                    // 先原子占租约：PENDING/PUBLISHED 都可由单一实例领取发送，其余实例得到 0 后跳过，
                    // 避免已发布但未确认的事件被多实例在同轮重复投递。
                    if (outbox.lease(row.getEventId(), LEASE_SECONDS) != 1) continue;
                    OrderCouponConsumeEvent event = json.readValue(row.getPayload(), OrderCouponConsumeEvent.class);
                    if (!event.valid() || !row.getEventId().equals(event.eventId())
                            || !row.getOrderNo().equals(event.orderNo())
                            || !row.getUserCouponId().equals(event.userCouponId())) {
                        throw new IllegalArgumentException("优惠券核销 Outbox 快照不一致");
                    }
                    boolean accepted = stream.send("couponPaid-out-0", MessageBuilder.withPayload(event)
                            .setHeader("KEYS", event.eventId()).build());
                    if (!accepted) throw new IllegalStateException("Broker 未确认接收优惠券核销事件");
                    if (outbox.markPublished(event.eventId()) != 1) {
                        throw new IllegalStateException("优惠券核销 Outbox 状态未能更新");
                    }
                } catch (Exception exception) {
                    try {
                        outbox.retryLater(row.getEventId(), exception.getClass().getSimpleName());
                    } catch (RuntimeException retryFailure) {
                        log.error("优惠券核销事件退避记录失败，eventId={}", row.getEventId(), retryFailure);
                    }
                    log.warn("优惠券核销事件发布失败，保留 Outbox 等待重试，eventId={}", row.getEventId(), exception);
                }
            }
        } catch (RuntimeException exception) {
            log.error("读取优惠券核销 Outbox 失败；待发记录保留，后续调度继续重试", exception);
        }
    }
}
