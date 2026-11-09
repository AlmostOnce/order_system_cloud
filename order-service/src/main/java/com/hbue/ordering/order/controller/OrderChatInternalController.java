package com.hbue.ordering.order.controller;
import com.hbue.ordering.common.response.ApiResponse;
import com.hbue.ordering.common.core.error.CommonErrorCode;
import com.hbue.ordering.common.core.exception.BusinessException;
import com.hbue.ordering.order.api.vo.OrderChatContextVO;
import com.hbue.ordering.common.web.security.InternalScopeTokenVerifier;
import com.hbue.ordering.order.service.OrderService;
import com.hbue.ordering.order.service.OrderWindowNameService;
import com.hbue.ordering.order.vo.OrderDetailVO;
import java.util.List;
import org.springframework.web.bind.annotation.*;
/** 订单聊天上下文内部接口。以共享范围密钥认证，只输出聊天授权所需事实；不会创建会话，也不会改变订单状态。 */
@RestController
@RequestMapping("/internal/orders")
public class OrderChatInternalController {
    private final OrderService orders;
    private final OrderWindowNameService names;
    private final InternalScopeTokenVerifier tokens;
    /** 注入订单查询、店铺名称补全和内部范围密钥校验组件，不在构造期间访问业务数据。 */
    public OrderChatInternalController(OrderService orders, OrderWindowNameService names, InternalScopeTokenVerifier tokens) {
        this.orders=orders;
        this.names=names;
        this.tokens=tokens;
    }
    /** 返回通知服务判定聊天归属与期限的最小订单事实；此接口不代替通知服务的顾客/店主授权检查。 */
    @GetMapping("/{orderId}/chat-context")
    public ApiResponse<OrderChatContextVO> context(@PathVariable("orderId") Long id,
    @RequestHeader(value="X-Internal-Token",required=false) String token) {
        // 先校验内部密钥再查订单，避免外部请求通过此接口读取订单归属。
        tokens.verify(token);
        var order=orders.getById(id);
        if(order==null) throw new BusinessException(CommonErrorCode.NOT_FOUND,"订单不存在");
        // 复用订单既有名称补全逻辑，聊天摘要展示真实店铺名，不拼接“窗口+ID”。
        var view=names.enrich(List.of(OrderDetailVO.from(order))).getFirst();
        // 售后等后续操作仍会更新updatedAt；沟通期限必须使用首次核销时间，防止不断延长24小时窗口。
        return ApiResponse.success(new OrderChatContextVO(id,order.getOrderNo(),order.getUserId(),order.getWindowId(),
        view.getWindowName(),order.getStatus(),"已完成".equals(order.getStatus())?order.getCompletedAt():null));
    }
}
