package com.hbue.ordering.comment.task;
import com.hbue.ordering.comment.model.CommentContentDO;
import com.hbue.ordering.comment.repository.CommentRepository;
import com.hbue.ordering.comment.repository.CommentContentRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 保守清理24小时前的待确认文档；SQL不可用时不删除，避免把暂时读不到当作不存在。 */
@Component
public class CommentOrphanCleaner {
    private static final Logger log=LoggerFactory.getLogger(CommentOrphanCleaner.class);
    private final CommentContentRepository contents;
    private final CommentRepository rows;
    public CommentOrphanCleaner(CommentContentRepository contents,CommentRepository rows) {this.contents=contents;this.rows=rows;}
    @Scheduled(fixedDelayString="${comment.orphan-scan-ms:3600000}")
    public void reconcile() {
        try {
            for(var content:contents.findUncommittedOlderThan(Instant.now().minus(24,ChronoUnit.HOURS),100)) {
                if(rows.byId(content.id())!=null)contents.markCommittedIfPending(content.id());
                else contents.removeIfPending(content.id());
            }
        } catch(RuntimeException exception) {log.warn("评论跨库对账未完成，保留待确认正文，原因={}",exception.getClass().getSimpleName());}
    }
}
