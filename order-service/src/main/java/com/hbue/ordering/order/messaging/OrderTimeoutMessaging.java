package com.hbue.ordering.order.messaging;
import com.hbue.ordering.order.dto.OrderTimeoutEvent;
import com.hbue.ordering.order.service.OrderTimeoutService;
import com.hbue.ordering.order.service.OrderTimeoutOutboxService;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.annotation.*;
/** 仅扫描待发送事件；不扫描订单。失败消费交由Broker重试及死信。 */
@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="order.payment-timeout.enabled",havingValue="true")
public class OrderTimeoutMessaging {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(OrderTimeoutMessaging.class);
    private final OrderTimeoutOutboxService outbox;
    private final OrderTimeoutService timeouts;
    private final StreamBridge stream;
    public OrderTimeoutMessaging(OrderTimeoutOutboxService outbox,OrderTimeoutService timeouts,StreamBridge stream) {
        this.outbox=outbox;this.timeouts=timeouts;this.stream=stream;
    }
    @Bean public Consumer<OrderTimeoutEvent> orderPaymentTimeout() {
        return event->{if(event==null || !event.valid())throw new IllegalArgumentException("无效超时消息");long remaining=timeouts.remainingMillis(event.orderId());
            if(remaining>0)send(event,remaining);else timeouts.expire(event.orderId());};
    }
    @Bean public Consumer<ErrorMessage> orderTimeoutRetryError() {
        return error->{throw new IllegalStateException("订单超时处理失败，交由Broker重试",error.getPayload());};
    }
    @Bean public Consumer<OrderTimeoutEvent> orderTimeoutDeadLetter() {
        return event->{
            if(event==null || !event.valid()) {log.error("ORDER_TIMEOUT_DEAD_LETTER：死信事件格式异常，需人工核查");return;}
            outbox.deadLetter(event.eventId());
            log.error("ORDER_TIMEOUT_DEAD_LETTER：超时订单需人工补偿，orderId={}，eventId={}",event.orderId(),event.eventId());
        };
    }
    @Scheduled(fixedDelayString="${order.payment-timeout.publish-delay-ms:1000}")
    public void publish() {
        try {
            for(var event:outbox.pending()) {
                try {
                    if(!event.valid())throw new IllegalArgumentException("无效待发送事件");
                    send(event,Math.max(0,event.expiresAt()-System.currentTimeMillis()));
                    outbox.sent(event.eventId());
                } catch(Exception error) {
                    outbox.failed(event.eventId(),error.getClass().getSimpleName());
                    log.error("ORDER_TIMEOUT_PUBLISH_RETRY：超时事件待重试，eventId={}",event.eventId(),error);
                }
            }
        } catch(RuntimeException error) {log.error("ORDER_TIMEOUT_OUTBOX_UNAVAILABLE：无法读取超时事件",error);}
    }
    private void send(OrderTimeoutEvent event,long remaining) {
        var message=MessageBuilder.withPayload(event).setHeader("KEYS",event.eventId());
        int level=delayLevel(remaining);
        if(level>0)message.setHeader("DELAY",level);
        if(!stream.send("orderTimeout-out-0",message.build()))throw new IllegalStateException("Broker未确认接收");
    }
    /** 选择不超过剩余时间的最大延迟；不足1秒用1秒，消费者按数据库期限复核。 */
    static int delayLevel(long millis) {
        if(millis<=0)return 0;
        long[] seconds={1,5,10,30,60,120,180,240,300,360,420,480,540,600,1200,1800,3600,7200,900};
        int level=1;long best=0;
        for(int i=0;i<seconds.length;i++)if(seconds[i]*1000<=millis && seconds[i]>best){best=seconds[i];level=i+1;}
        return level;
    }
}
