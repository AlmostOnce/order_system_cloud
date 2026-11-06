package com.hbue.ordering.order.api.vo;
import java.time.LocalDateTime;
/**
 * 受内部令牌保护的订单沟通事实；仅供服务间授权判断，不直接返回学生端。
 * @param orderId 订单内部主键，与创建会话请求关联
 * @param orderNo 订单业务号，供会话摘要展示
 * @param customerId 订单真实顾客内部编号，用于核验当前操作者归属
 * @param windowId 订单窗口编号，用于店主直接经营授权校验
 * @param windowName 已复用订单名称服务补全的店铺名
 * @param status 订单当前真实状态，不能由聊天客户端指定
 * @param completedAt 已完成订单的核销时间；其他状态为空，不用最后聊天时间续期
 */
public record OrderChatContextVO(Long orderId, String orderNo, Long customerId, Long windowId,
String windowName, String status, LocalDateTime completedAt) {
}
