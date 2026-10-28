package com.hbue.ordering.comment.task;
import com.hbue.ordering.comment.model.CommentContent;
import com.hbue.ordering.comment.repository.CommentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 保守清理24小时前的待确认文档；SQL不可用时不删除，避免把暂时读不到当作不存在。 */
@Component
public class CommentOrphanCleaner {
    private static final Logger log=LoggerFactory.getLogger(CommentOrphanCleaner.class);
    private final MongoTemplate mongo;
    private final CommentRepository rows;
    public CommentOrphanCleaner(MongoTemplate mongo,CommentRepository rows) {this.mongo=mongo;this.rows=rows;}
    @Scheduled(fixedDelayString="${comment.orphan-scan-ms:3600000}")
    public void reconcile() {
        try {
            var query=Query.query(Criteria.where("committed").is(false)
                    .and("createdAt").lt(Instant.now().minus(24,ChronoUnit.HOURS))).limit(100);
            for(var content:mongo.find(query,CommentContent.class)) {
                var id=Query.query(Criteria.where("_id").is(content.id()).and("committed").is(false));
                if(rows.byId(content.id())!=null)mongo.updateFirst(id,Update.update("committed",true),CommentContent.class);
                else mongo.remove(id,CommentContent.class);
            }
        } catch(RuntimeException exception) {log.warn("评论跨库对账未完成，保留待确认正文，原因={}",exception.getClass().getSimpleName());}
    }
}
