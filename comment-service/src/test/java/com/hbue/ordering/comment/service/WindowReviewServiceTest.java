package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.dto.UpdateWindowReviewRequest;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.service.impl.WindowReviewServiceImpl;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WindowReviewServiceTest {
    @Test
    void ratingOnlyAllowsOmittedTextImagesAndTags() {
        var rows = mock(WindowReviewMapper.class);
        var orders = mock(CommentOrderClient.class);
        when(orders.getOrder(7L, "Bearer signed")).thenReturn(ApiResponse.success(
                new OrderReviewSnapshotVO(7L, 2L, "已完成", List.of())));
        var service = new WindowReviewServiceImpl(rows, orders, mock(ManagedImageClient.class),
                new ObjectMapper(), mock(AuthorAvatarService.class), mock(ReviewAuthorityService.class),
                mock(ReviewDiscussionService.class));
        var result = service.create(9L, "同学", "Bearer signed", "a1111111-1111-4111-8111-111111111111",
                new CreateWindowReviewRequest(7L, 5, null, null, null, null));
        assertEquals(5, result.score());
        assertEquals("", result.content());
        assertTrue(result.images().isEmpty());
        assertTrue(result.tags().isEmpty());
        verify(rows).insert(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 切换筛选后沿用旧游标必须提示重新加载，而不是静默返回空页。
     */
    @Test
    void pageRejectsCursorThatDoesNotMatchCurrentFilter() {
        var rows = mock(WindowReviewMapper.class);
        String cursor = "a1111111-1111-4111-8111-111111111111";
        // 按当前筛选回读游标为 0，说明游标属于旧筛选或已被删除。
        when(rows.countScopedCursor(cursor, 9L, null, 5, null)).thenReturn(0);
        var service = new WindowReviewServiceImpl(rows, mock(CommentOrderClient.class),
                mock(ManagedImageClient.class), new ObjectMapper(), mock(AuthorAvatarService.class),
                mock(ReviewAuthorityService.class), mock(ReviewDiscussionService.class));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.page(9L, null, cursor, 20, 5, null));

        assertEquals(CommonErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(rows, never()).pageByUser(anyLong(), anyString(), anyInt(), any(), any());
    }

    /**
     * 修改店铺评价时未传的字段保持原值，传空数组表示清空。
     */
    @Test
    void reviseKeepsOmittedFieldsAndClearsExplicitEmptyArrays() throws Exception {
        var rows = mock(WindowReviewMapper.class);
        var json = new ObjectMapper();
        String id = "a1111111-1111-4111-8111-111111111111";
        when(rows.byId(id)).thenReturn(new WindowReviewMapper.Row(id, 9L, "同学", 7L, 2L, 5, "旧正文",
                json.writeValueAsString(List.of("0123456789abcdef0123456789abcdef")),
                json.writeValueAsString(List.of("好吃")), id, "hash", java.time.LocalDateTime.now(), false));
        when(rows.update(anyString(), anyInt(), anyString(), anyString(), anyString())).thenReturn(1);

        var service = new WindowReviewServiceImpl(rows, mock(CommentOrderClient.class),
                mock(ManagedImageClient.class), json, mock(AuthorAvatarService.class),
                mock(ReviewAuthorityService.class), mock(ReviewDiscussionService.class));
        var result = service.revise(9L, List.of("CUSTOMER"), id,
                new UpdateWindowReviewRequest(4, "新正文", List.of(), List.of()));

        assertEquals(4, result.score());
        assertEquals("新正文", result.content());
        assertTrue(result.images().isEmpty());
        assertTrue(result.tags().isEmpty());
        verify(rows).update(id, 4, "新正文", "[]", "[]");
    }

    /**
     * 删除店铺评价走软删除，且只影响未删除的记录。
     */
    @Test
    void removeSoftDeletesPublishedReview() throws Exception {
        var rows = mock(WindowReviewMapper.class);
        String id = "a1111111-1111-4111-8111-111111111111";
        when(rows.byId(id)).thenReturn(new WindowReviewMapper.Row(id, 9L, "同学", 7L, 2L, 5, "正文", "[]", "[]",
                id, "hash", java.time.LocalDateTime.now(), false));
        when(rows.softDelete(id)).thenReturn(1);

        var service = new WindowReviewServiceImpl(rows, mock(CommentOrderClient.class),
                mock(ManagedImageClient.class), new ObjectMapper(), mock(AuthorAvatarService.class),
                mock(ReviewAuthorityService.class), mock(ReviewDiscussionService.class));
        service.remove(9L, List.of("CUSTOMER"), id);

        verify(rows).softDelete(id);
    }
}
