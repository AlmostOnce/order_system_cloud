package com.hbue.ordering.comment.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import com.hbue.ordering.comment.mapper.CommentWriteMapper;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="comment.messaging.enabled",havingValue="true")
public class RatingEventPublisher {
    private static final Logger log=LoggerFactory.getLogger(RatingEventPublisher.class);
    private final CommentWriteMapper mapper;
    private final ObjectMapper json;
    private final StreamBridge stream;
    public RatingEventPublisher(CommentWriteMapper mapper,ObjectMapper json,StreamBridge stream) {this.mapper=mapper;this.json=json;this.stream=stream;}
    @Scheduled(fixedDelayString="${comment.publish-delay-ms:1000}")
    public void publish() {
        try {
            var pending=mapper.pending();
            for(var row:pending) {
                try {
                    var event=json.readValue(row.payload(),ProductRatingChangedEvent.class);
                    if(!event.valid()||!row.id().equals(event.eventId()))throw new IllegalArgumentException("评分事件格式不正确");
                    if(!stream.send("commentRating-out-0",MessageBuilder.withPayload(event).setHeader("KEYS",row.id()).build()))
                        throw new IllegalStateException("Broker未确认评分事件");
                    mapper.markPublished(row.id());
                } catch(Exception exception) {
                    mapper.retry(row.id(), exception.getClass().getSimpleName());
                    log.warn("评分事件发布待重试，eventId={}",row.id());
                }
            }
        } catch(RuntimeException exception) {log.warn("评分待发送事件扫描失败，原因={}",exception.getClass().getSimpleName());}
    }
    /** 重投每个菜品的最新快照；商品投影被清空或死信遗漏时可自行恢复，重复不会累加。 */
    @Scheduled(initialDelayString="${comment.rating-reconcile-ms:300000}",fixedDelayString="${comment.rating-reconcile-ms:300000}")
    public void republishLatest() {
        try {mapper.republishLatest();}
        catch(RuntimeException exception) {log.warn("评分最新快照重投待下次对账");}
    }
}
