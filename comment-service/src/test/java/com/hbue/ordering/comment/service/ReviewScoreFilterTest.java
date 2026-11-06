package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.mapper.CommentMapper;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.service.impl.CommentServiceImpl;
import com.hbue.ordering.comment.service.impl.WindowReviewServiceImpl;
import com.hbue.ordering.common.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 两种评价共用筛选语义；非法分数必须在数据库查询之前拒绝。 */
class ReviewScoreFilterTest {
    @Test void onlyOneAndFiveAreAcceptedAndPageRemainsBounded() {
        var comments = mock(CommentMapper.class);
        var windows = mock(WindowReviewMapper.class);
        var avatars = mock(AuthorAvatarService.class);
        var service = new CommentServiceImpl(comments, null, null, null, null, new ObjectMapper(), avatars);
        var shops = new WindowReviewServiceImpl(windows, null, null, new ObjectMapper(), avatars);
        for (int score : new int[] {0, 2, 3, 4, 6}) {
            assertThrows(BusinessException.class, () -> service.page(null, 1L, null, 20, score, null));
            assertThrows(BusinessException.class, () -> shops.page(null, 1L, null, 20, score, null));
        }
        verifyNoInteractions(comments, windows);
        for (Integer score : new Integer[] {null, 1, 5}) {
            when(comments.page(null, 1L, null, 21, score, null)).thenReturn(List.of());
            when(windows.page(null, 1L, null, 21, score, null)).thenReturn(List.of());
            assertFalse(service.page(null, 1L, null, 20, score, null).hasMore());
            assertFalse(shops.page(null, 1L, null, 20, score, null).hasMore());
            verify(comments).page(null, 1L, null, 21, score, null);
            verify(windows).page(null, 1L, null, 21, score, null);
        }
    }
    /** 未回填的旧记录不能在有图分类中悄悄消失。 */
    @Test void unknownImageMetadataRejectsPhotoFilterInsteadOfReturningFalseEmpty() {
        var rows = mock(CommentMapper.class);
        when(rows.hasUnknownImages(null, 7L)).thenReturn(true);
        var service = new CommentServiceImpl(rows, null, null, null, null, new ObjectMapper(), null);
        assertThrows(BusinessException.class, () -> service.page(null, 7L, null, 20, null, true));
        verify(rows).hasUnknownImages(null, 7L);
        verifyNoMoreInteractions(rows);
    }
}
