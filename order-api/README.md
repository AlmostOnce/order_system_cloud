# order-api 订单契约模块

> order-service 的内部调用契约：订单快照查询、支付确认、券核销回执、销量快照、聊天上下文、推荐购买快照事件。

## Feign 客户端

| 客户端 | 用途 | 降级约定 |
|---|---|---|
| `OrderServiceClient` | 购物车读订单快照（透传用户 Bearer） | 降级工厂按状态映射 401/403/404/503，绝不伪造订单成功 |
| `OrderChatClient` | 通知服务读订单沟通上下文 | 内部令牌 |
| `OrderCouponConsumeAckClient` | 优惠券服务确认订单侧 Outbox 核销 | 事件 ID + 订单号 + 券 ID 三重匹配 |
| `OrderSalesInternalClient` | 统计服务读支付销量快照（V1 兼容） | 仅订单服务直连 |

## 模型

`OrderCartSnapshotVO`、`OrderChatContextVO`、`OrderPaidSalesSnapshotVO`、`OrderReviewSnapshotVO`、`OrderPaidCommand`、`CouponConsumeConfirmationRequest`、`RecommendationPurchaseSnapshotEvent/PageVO`（MQ 事件契约）。

## 使用约定

被购物车、通知、推荐、统计、支付、优惠券、评价服务依赖；订单归属校验由订单服务完成，调用方不重复实现。
