package com.hbue.ordering.comment.service;

import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.comment.service.impl.OrderReviewServiceImpl;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderReviewServiceTest {
    private final CommentOrderClient orders = mock(CommentOrderClient.class);
    private final CommentService comments = mock(CommentService.class);
    private final WindowReviewService windows = mock(WindowReviewService.class);
    private final OrderReviewService service = new OrderReviewServiceImpl(orders, comments, windows);

    @Test void failedOwnershipCheckDoesNotQueryReviews() {
        when(orders.getOrder(7L, "Bearer signed")).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.get(9L, 7L, "Bearer signed"));
        verifyNoInteractions(comments, windows);
    }
    @Test void incompleteOrderCannotBeReviewed() {
        when(orders.getOrder(7L, "Bearer signed")).thenReturn(ApiResponse.success(
                new OrderReviewSnapshotVO(7L, 2L, "待取货", List.of())));
        assertThrows(BusinessException.class, () -> service.get(9L, 7L, "Bearer signed"));
        verifyNoInteractions(comments, windows);
    }
    @Test void completedOrderQueriesOnlyAuthenticatedUsersReviews() {
        when(orders.getOrder(7L, "Bearer signed")).thenReturn(ApiResponse.success(
                new OrderReviewSnapshotVO(7L, 2L, "已完成", List.of())));
        when(comments.forOrder(9L, 7L)).thenReturn(List.of());
        var result = service.get(9L, 7L, "Bearer signed");
        assertEquals(7L, result.orderId());
        assertNull(result.windowReview());
        verify(windows).forOrder(9L, 7L);
        verify(comments).forOrder(9L, 7L);
    }
}
