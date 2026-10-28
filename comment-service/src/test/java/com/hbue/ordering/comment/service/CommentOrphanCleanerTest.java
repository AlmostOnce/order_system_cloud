package com.hbue.ordering.comment.service;
import com.hbue.ordering.comment.model.CommentContent;
import com.hbue.ordering.comment.repository.CommentRepository;
import com.hbue.ordering.comment.task.CommentOrphanCleaner;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CommentOrphanCleanerTest {
    @Test void databaseFailureMustNotDeleteContent() {
        MongoTemplate mongo=mock(MongoTemplate.class);
        CommentRepository rows=mock(CommentRepository.class);
        when(mongo.find(any(Query.class),eq(CommentContent.class))).thenReturn(List.of(
                new CommentContent("id","内容",List.of(),List.of(),Instant.EPOCH,false)));
        when(rows.byId("id")).thenThrow(new IllegalStateException("SQL unavailable"));
        new CommentOrphanCleaner(mongo,rows).reconcile();
        verify(mongo,never()).remove(any(Query.class),eq(CommentContent.class));
    }
}
