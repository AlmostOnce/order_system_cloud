# coupon-api 优惠券契约模块

> coupon-service 的内部调用契约：锁券与释放，以及支付成功核销事件模型。

## Feign 客户端

| 客户端 | 用途 | 降级约定 |
|---|---|---|
| `CouponServiceClient` | `/internal/coupons/lock` 锁券、`/release` 释放（仅释放绑定指定订单且仍锁定的券） | 降级工厂 fail-closed：券服务不可用不得解释为「未选券」或「锁券成功」，统一服务不可用让调用方 Seata 事务回滚 |

## 模型

`CouponLockRequest/VO`、`CouponReleaseRequest`、`CouponPaidEvent`（支付成功核销事件，MQ 契约）。

## 使用约定

被订单、优惠券服务（自引用）依赖；锁券/释放必须在调用方 Seata 全局事务内完成，核销走订单 Outbox 异步。
