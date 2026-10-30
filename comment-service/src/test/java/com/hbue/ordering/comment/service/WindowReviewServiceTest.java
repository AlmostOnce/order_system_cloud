package com.hbue.ordering.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.dto.CreateWindowReviewRequest;
import com.hbue.ordering.comment.mapper.WindowReviewMapper;
import com.hbue.ordering.comment.service.impl.WindowReviewServiceImpl;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.web.media.ManagedImageClient;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowReviewServiceTest {
    @Test void ratingOnlyAllowsOmittedTextImagesAndTags() {
        var rows = mock(WindowReviewMapper.class);
        var orders = mock(CommentOrderClient.class);
        when(orders.getOrder(7L, "Bearer signed")).thenReturn(ApiResponse.success(
                new OrderReviewSnapshotVO(7L, 2L, "已完成", List.of())));
        var service = new WindowReviewServiceImpl(rows, orders, mock(ManagedImageClient.class),
                new ObjectMapper(), mock(AuthorAvatarService.class));
        var result = service.create(9L, "同学", "Bearer signed", "a1111111-1111-4111-8111-111111111111",
                new CreateWindowReviewRequest(7L, 5, null, null, null));
        assertEquals(5, result.score());
        assertEquals("", result.content());
        assertTrue(result.images().isEmpty());
        assertTrue(result.tags().isEmpty());
        verify(rows).insert(org.mockito.ArgumentMatchers.any());
    }
}
