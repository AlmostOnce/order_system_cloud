package com.hbue.ordering.comment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.mapper.CommentWriteMapper;
import com.hbue.ordering.comment.model.CommentRowDO;
import com.hbue.ordering.comment.service.RatingSummaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 校验评价元数据写入与评分缓存失效的时序：删除缓存必须排在本地事务提交之后。
 */
class CommentMetadataWriterImplTest {

    private final CommentWriteMapper mapper = mock(CommentWriteMapper.class);
    private final RatingSummaryService ratings = mock(RatingSummaryService.class);
    private final CommentMetadataWriterImpl writer = new CommentMetadataWriterImpl(mapper, new ObjectMapper(), ratings);

    /**
     * 每个用例后清理线程绑定的同步器，避免影响后续测试。
     */
    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * 新增评价：提交前不删除缓存，提交后才删除，防止并发读者用旧库值回填。
     */
    @Test
    void newCommentEvictsRatingCacheOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(mapper.lockRating(9L)).thenReturn(new CommentWriteMapper.RatingSnapshot(1L, 1L, 5L));
        var row = new CommentRowDO("c-1", 7L, "学生", 3L, 9L, 1L, 5,
                "req-1", "hash-1", LocalDateTime.now(), false, false);

        writer.commit(row);

        verify(ratings, never()).evict(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(ratings).evict(9L);
    }

    /**
     * 删除评价：同样只在提交后失效缓存，保证批量分和单条分读取到最新事实。
     */
    @Test
    void removingCommentEvictsRatingCacheOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(mapper.deleteComment("c-1")).thenReturn(1);
        when(mapper.adjustRating(9L, -1, -1)).thenReturn(1);
        when(mapper.lockRating(9L)).thenReturn(new CommentWriteMapper.RatingSnapshot(2L, 0L, 0L));
        var row = new CommentRowDO("c-1", 7L, "学生", 3L, 9L, 1L, 1,
                "req-1", "hash-1", LocalDateTime.now(), false, false);

        writer.remove(row);

        verify(ratings, never()).evict(anyLong());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(ratings).evict(9L);
    }
}
