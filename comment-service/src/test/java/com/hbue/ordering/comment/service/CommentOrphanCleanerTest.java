package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.model.CommentContentDO;
import com.hbue.ordering.comment.repository.CommentContentRepository;
import com.hbue.ordering.comment.task.CommentOrphanCleaner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

class CommentOrphanCleanerTest {
    @Test
    void databaseFailureMustNotDeleteContent() {
        CommentContentRepository mongo = mock(CommentContentRepository.class);
        CommentMapper rows = mock(CommentMapper.class);
        when(mongo.findUncommittedOlderThan(any(), anyInt())).thenReturn(List.of(
                new CommentContentDO("id", "内容", List.of(), List.of(), Instant.EPOCH, false)));
        when(rows.byId("id")).thenThrow(new IllegalStateException("SQL unavailable"));
        new CommentOrphanCleaner(mongo, rows).reconcile();
        verify(mongo, never()).removeIfPending(anyString());
    }
}
