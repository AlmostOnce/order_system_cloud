package com.hbue.ordering.comment.service;
import com.hbue.ordering.comment.client.CommentOrderClient;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.vo.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseVerifierTest {
    @Test void completedOwnedOrderMustActuallyContainProduct() {
        CommentOrderClient client=mock(CommentOrderClient.class);
        PurchaseVerifier verifier=new PurchaseVerifier(client);
        when(client.getOrder(7L,"Bearer signed")).thenReturn(ApiResponse.success(
                new OrderReviewSnapshotVO(7L,2L,"已完成",List.of(new OrderPurchasedItemVO(5L,2)))));
        assertEquals(2L,verifier.requireCompletedPurchase(7L,5L,"Bearer signed"));
        assertThrows(BusinessException.class,()->verifier.requireCompletedPurchase(7L,6L,"Bearer signed"));
        verify(client,times(2)).getOrder(7L,"Bearer signed");
    }
    @Test void unpaidCancelledOrUnavailableOrderCannotBeReviewed() {
        CommentOrderClient client=mock(CommentOrderClient.class);
        PurchaseVerifier verifier=new PurchaseVerifier(client);
        for(String status:List.of("待支付","已取消","已支付","制作中","待取货")) {
            when(client.getOrder(7L,"Bearer signed")).thenReturn(ApiResponse.success(
                    new OrderReviewSnapshotVO(7L,2L,status,List.of(new OrderPurchasedItemVO(5L,2)))));
            assertThrows(BusinessException.class,()->verifier.requireCompletedPurchase(7L,5L,"Bearer signed"));
        }
        when(client.getOrder(7L,"Bearer signed")).thenReturn(null);
        assertThrows(BusinessException.class,()->verifier.requireCompletedPurchase(7L,5L,"Bearer signed"));
    }
}
