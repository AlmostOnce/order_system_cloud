package com.hbue.ordering.order.api.client;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
/** 订单沟通内部事实查询契约。调用方必须携带服务端共享密钥，并在收到事实后校验顾客归属或窗口经营授权。 */
@FeignClient(name="order-service", contextId="notificationOrderChat")
public interface OrderChatClient {
    /** 读取订单当前沟通上下文；token来自通知服务配置，调用失败时业务层拒绝授权，不把旧状态当作支付证明。 */
    @GetMapping("/internal/orders/{orderId}/chat-context")
    ApiResponse<OrderChatContextVO> context(@PathVariable("orderId") Long orderId, @RequestHeader("X-Internal-Token") String token);
}
