package com.hbue.ordering.comment.client;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.vo.OrderReviewSnapshotVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name = "order-service", contextId = "commentOrderClient")
public interface CommentOrderClient {
    @GetMapping("/orders/{id}")
    ApiResponse<OrderReviewSnapshotVO> getOrder(@PathVariable("id") Long id,
            @RequestHeader("Authorization") String authorization);
}
