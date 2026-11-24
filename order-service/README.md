# order-service 订单服务

> 负责订单创建、状态流转、支付确认、取消、取餐码核销、退款售后和订单事件可靠投递。默认端口 `9091`，数据库 `order_db`，下单与取消通过 Seata AT 协调库存与优惠券。

## 职责与边界

- 保存订单主表与菜品明细快照，记录金额、优惠分摊、状态、支付时间、取餐码和幂等信息。
- 创建订单：校验顾客与购买参数 → 商品报价 → 锁券（如有）→ 扣库存 → 保存订单，跨服务步骤处于同一 Seata 全局事务。
- 支付事实：首次确认支付时把支付服务的 `success_time` 写入 `orders.paid_at`，状态 CAS、支付时间与事件 outbox 同库同事务提交。
- 取消：待支付订单可取消，按原扣减流水回补库存；重复取消不重复回补。
- 履约：商家只能按允许的转换推进状态，不能直接标记已支付或已完成。
- 取餐码：顾客付款后生成六位取餐码，商家凭码核销完成订单。
- 退款：支持按明细申请部分退款、商家/管理员审核、顾客申诉与撤回、支付退款和库存回补。
- 事件：支付销量、优惠券核销、订单通知、推荐购买快照、支付超时都通过本地消息表可靠发布。

**边界：** 订单不直接扣减余额、不直接改库存表、不直接核销优惠券；这些动作分别通过支付、库存、优惠券服务完成。

## 订单状态机

| 状态 | 含义 | 允许流转 |
|---|---|---|
| `PENDING_PAYMENT` 待支付 | 等待顾客支付 | → `PAID`、`CANCELLED` |
| `PAID` 已支付 | 支付成功，等待商家接单 | → `PREPARING` |
| `PREPARING` 制作中 | 商家制作中 | → `WAITING_FOR_PICKUP` |
| `WAITING_FOR_PICKUP` 待取货 | 等待顾客取餐 | → `COMPLETED` |
| `COMPLETED` 已完成 | 生命周期结束 | 无 |
| `CANCELLED` 已取消 | 未支付取消 | 无 |
| `REFUNDED` 已退款 | 未完成订单全部退款 | 无 |

商家推进额外受限：不能从待支付出发、不能改成取消或完成，已完成必须由取餐码核销入口触发。所有状态更新使用原状态条件更新与版本号，避免支付与取消并发时双方都成为赢家。

## 创建与支付

1. 客户端提交下单请求，携带 `Idempotency-Key`。
2. 服务端计算规范化请求摘要 `request_hash`；同一用户同一幂等键重复提交时返回原订单，内容不同则冲突。
3. 商品服务报价确定当前价格与规格，不信任客户端金额。
4. 如有优惠券，在当前全局事务内调用优惠券服务锁券；锁券失败则整单失败。
5. 调用库存服务按订单业务标识扣减并记录流水。
6. 保存订单、明细与优惠分摊快照；订单保存 `totalAmount` 原价、`discountAmount` 优惠额、`payableAmount` 应付额。
7. 支付服务确认支付后，内部接口把订单推进为已支付并写入支付时间；支付金额只按 `payableAmount`。

## 支付超时

- 订单创建时固定 15 分钟支付截止时间（`expires_at`），历史数据按原创建时间推导。
- 超时事件通过 `order_timeout_outbox` 发布到 `order-payment-timeout-v1`，消费者按订单状态条件取消并回补库存。
- 死信队列 `%DLQ%order-payment-timeout-v1` 由独立观察组处理。
- 默认 profile 为 `order-timeout`，其分组包含 `ranking-mq`、`order-timeout-mq`、`coupon-mq`。

## 履约与取餐码

- 顾客在订单详情通过 `GET /orders/{orderId}/pickup-code` 获取本人专属六位取餐码；首次读取时生成并持久化，不随普通列表/详情返回。
- 商家核销 `POST /orders/pickup/verify` 只提交取餐码，服务端按 `STALL_MANAGER` / `RESTAURANT_MANAGER` 角色与窗口范围校验，仅待取货可核销。
- 每商家账号每分钟最多 30 次尝试，错误与越权凭据也计数。
- 数据库以 `active_pickup_code` 生成列和唯一索引保证已支付、制作中、待取货订单取餐码唯一；碰撞时重新生成，完成或取消后自动释放。
- 状态、版本与取餐码一起条件更新，成功后清空取餐码并写入完成通知 outbox。

## 退款售后

- 可申请状态：`已支付`、`制作中`、`待取货`、`已完成`；已完成订单必须在完成时间起 72 小时内申请，精确到时刻。
- 退款金额按明细实付金额分摊，不采信前端金额；同一明细已有处理中申请占用额度，任意组合不得超过实付。
- 最后一份数量承接分摊余分，避免四舍五入造成多退或少退。
- 流程：顾客申请 → 商家/管理员审核（同意/拒绝）→ 同意后调用支付服务退款并回补库存；拒绝后顾客可申诉或撤回。
- 全部明细退款成功时订单进入 `REFUNDED`，禁止继续接单、制作或取餐。
- 详细规则见 [`docs/ORDER_REFUNDS.md`](../docs/ORDER_REFUNDS.md)。

## 幂等与事件

| 机制 | 说明 |
|---|---|
| 下单幂等 | `orders.idempotency_key` 用户维度唯一 + `request_hash` 内容校验 |
| 支付确认幂等 | 订单状态 CAS，只有首次支付写入 `paid_at` 和销量事件 |
| 取消回补幂等 | 库存服务按订单业务标识检查原扣减与已有回补流水 |
| 销量 outbox | `sales_event_outbox` → `order-paid-sales-v2`，统计服务去重消费 |
| 优惠券 outbox | `order_coupon_consume_outbox` → `order-coupon-paid-v1`，券服务核销后回执确认 |
| 通知 outbox | `order_notification_outbox` → `order-status-notification-v1` |
| 推荐 outbox | 已支付购买快照 → `recommendation-purchase-snapshot-v1` |
| 取消通知 TCC | Try 写 `PREPARED`，Confirm 本地事务写 outbox，Cancel 防悬挂；默认随 `ranking-mq` 启用，可用环境变量覆盖 |

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `POST /orders` | 创建订单 |
| `GET /orders/list` | 分页查询本人/权限范围内订单 |
| `GET /orders/{orderId}` | 订单详情 |
| `PATCH /orders/{orderId}/cancel` | 取消待支付订单 |
| `PATCH /orders/{orderId}/status` | 商家推进履约状态 |
| `GET /orders/{orderId}/pickup-code` | 顾客获取取餐码 |
| `POST /orders/pickup/verify` | 商家核销取餐码 |
| `POST /orders/{orderId}/refunds` | 申请退款 |
| `GET /orders/{orderId}/refunds` | 查询订单退款 |
| `POST /orders/{orderId}/refunds/{refundId}/appeal` | 申诉 |
| `POST /orders/{orderId}/refunds/{refundId}/withdraw` | 撤回申请 |
| `GET /orders/refunds/manage` | 商家/管理员退款列表 |
| `POST /orders/refunds/{refundId}/decision` | 审核退款 |
| `GET /orders/rankings/sales` | 订单侧销量榜兼容入口 |
| `GET /internal/orders/{orderId}` | 内部订单查询 |
| `PATCH /internal/orders/{orderId}/paid` | 支付成功确认 |
| `GET /internal/orders/{orderId}/paid-sales-snapshot` | 销量快照 |
| `POST /internal/orders/coupon-consume/confirmed` | 券核销回执 |
| `GET /internal/orders/{orderId}/chat-context` | 商家聊天上下文 |
| `GET /internal/users/{userId}/account-deletion/blockers` | 注销阻断项 |

## 数据表

| 表 | 作用 |
|---|---|
| `orders` | 订单主表：订单号、用户、窗口、金额、优惠快照、状态、备注、取餐码、版本、幂等键、支付时间、截止时间 |
| `order_items` | 明细快照：菜品编码/名称/单价、数量、行金额、优惠分摊、实付金额、规格快照 |
| `order_refunds` | 退款申请：明细、数量、金额、状态、原因、处理结果 |
| `order_refund_actions` | 退款操作流水：申请、审核、申诉、撤回等动作记录 |
| `sales_event_outbox` | 销量事件本地消息表 |
| `order_coupon_consume_outbox` | 优惠券核销事件 outbox |
| `order_notification_outbox` | 订单通知事件 outbox |
| `order_notification_tcc` | 取消通知 TCC 事务状态 |
| `order_timeout_outbox` | 支付超时事件 outbox |
| `recommendation_purchase_snapshots` 等 | 推荐购买快照与 outbox |
| `undo_log` | Seata AT 回滚日志 |

关键字段：

- `orders`：`order_no` 对外唯一、`total_amount` 原价、`discount_amount` 优惠、`payable_amount` 应付、`status` 中文状态、`pickup_code`、`expires_at`、`paid_at`、`idempotency_key`、`request_hash`。
- `order_items`：`product_code_snapshot`、`product_name_snapshot`、`unit_price_snapshot`、`quantity`、`line_amount`、`line_discount_amount`、`line_payable_amount`、规格快照。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`OrderController.java`](src/main/java/com/hbue/ordering/order/controller/OrderController.java) | 订单创建、列表、详情、取消、状态推进 |
| [`OrderServiceImpl.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderServiceImpl.java) | 下单与状态编排 |
| [`OrderPersistenceServiceImpl.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderPersistenceServiceImpl.java) | 订单持久化与事务边界 |
| [`OrderStatus.java`](src/main/java/com/hbue/ordering/order/enums/OrderStatus.java) | 状态与合法流转 |
| [`OrderPickupServiceImpl.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderPickupServiceImpl.java) | 取餐码生成与核销 |
| [`OrderRefundServiceImpl.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderRefundServiceImpl.java) | 退款流程 |
| [`RefundRules.java`](src/main/java/com/hbue/ordering/order/service/RefundRules.java) | 退款纯规则 |
| [`OrderTimeoutServiceImpl.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderTimeoutServiceImpl.java) | 支付超时取消 |
| [`SalesOutboxWriter.java`](src/main/java/com/hbue/ordering/order/ranking/SalesOutboxWriter.java) | 销量 outbox 写入 |
| [`CouponConsumeOutboxWriter.java`](src/main/java/com/hbue/ordering/order/service/CouponConsumeOutboxWriter.java) | 券核销 outbox 写入 |
| [`CancellationNotificationTccImpl.java`](src/main/java/com/hbue/ordering/order/notification/CancellationNotificationTccImpl.java) | 取消通知 TCC |
| [`OrderAccountDeletionService.java`](src/main/java/com/hbue/ordering/order/service/impl/OrderAccountDeletionService.java) | 注销阻断项 |

## 迁移与运行

- 迁移脚本位于 [`docs/database/migrations/order_db`](../docs/database/migrations/order_db)；注意存在两个 `V10` 前缀文件，执行前按内容与依赖顺序核对。
- 涉及金额、状态和 outbox 的变更必须在同一本地事务提交。
- 支付超时、通知、销量、券核销依赖对应 MQ profile；容器启动不代表消费链路已验证。
- 排查订单/库存异常时，先看订单状态、库存流水、Seata 与 outbox，不要手工重复扣减或回补。
- 详细设计见 [`docs/ORDER_PAYMENT_TIMEOUT.md`](../docs/ORDER_PAYMENT_TIMEOUT.md)、[`docs/ORDER_PAGINATION.md`](../docs/ORDER_PAGINATION.md)、[`docs/SALES_RANKING.md`](../docs/SALES_RANKING.md)。
