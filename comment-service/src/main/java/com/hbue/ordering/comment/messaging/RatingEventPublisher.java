package com.hbue.ordering.comment.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.api.ProductRatingChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="comment.messaging.enabled",havingValue="true")
public class RatingEventPublisher {
    private static final Logger log=LoggerFactory.getLogger(RatingEventPublisher.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StreamBridge stream;
    public RatingEventPublisher(JdbcTemplate jdbc,ObjectMapper json,StreamBridge stream) {this.jdbc=jdbc;this.json=json;this.stream=stream;}
    private record Pending(String id,String payload) {}
    @Scheduled(fixedDelayString="${comment.publish-delay-ms:1000}")
    public void publish() {
        try {
            var pending=jdbc.query("""
                SELECT event_id,payload FROM comment_rating_outbox
                WHERE published_at IS NULL AND next_attempt_at<=NOW() ORDER BY next_attempt_at,event_id LIMIT 50
                """,(r,n)->new Pending(r.getString(1),r.getString(2)));
            for(var row:pending) {
                try {
                    var event=json.readValue(row.payload(),ProductRatingChangedEvent.class);
                    if(!event.valid()||!row.id().equals(event.eventId()))throw new IllegalArgumentException("评分事件格式不正确");
                    if(!stream.send("commentRating-out-0",MessageBuilder.withPayload(event).setHeader("KEYS",row.id()).build()))
                        throw new IllegalStateException("Broker未确认评分事件");
                    jdbc.update("UPDATE comment_rating_outbox SET published_at=NOW(),last_error=NULL WHERE event_id=? AND published_at IS NULL",row.id());
                } catch(Exception exception) {
                    jdbc.update("""
                        UPDATE comment_rating_outbox SET next_attempt_at=TIMESTAMPADD(SECOND,POW(2,LEAST(attempts,8)),NOW()),
                        attempts=attempts+1,last_error=? WHERE event_id=? AND published_at IS NULL
                        """,exception.getClass().getSimpleName(),row.id());
                    log.warn("评分事件发布待重试，eventId={}",row.id());
                }
            }
        } catch(RuntimeException exception) {log.warn("评分待发送事件扫描失败，原因={}",exception.getClass().getSimpleName());}
    }
    /** 重投每个菜品的最新快照；商品投影被清空或死信遗漏时可自行恢复，重复不会累加。 */
    @Scheduled(initialDelayString="${comment.rating-reconcile-ms:300000}",fixedDelayString="${comment.rating-reconcile-ms:300000}")
    public void republishLatest() {
        try {jdbc.update("""
            UPDATE comment_rating_outbox o JOIN comment_rating_stats s ON o.product_id=s.product_id AND o.revision=s.revision
            SET o.published_at=NULL WHERE o.published_at IS NOT NULL
            """);}
        catch(RuntimeException exception) {log.warn("评分最新快照重投待下次对账");}
    }
}
