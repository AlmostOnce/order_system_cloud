# payment-service 支付服务

> 负责支付单创建、支付渠道编排、渠道回调验签、订单支付门闩、支付关闭与退款。默认端口 `9101`，数据库 `payment_db`。

## 职责与边界

- 为订单创建支付单，保存应付金额快照、渠道、状态与到账结果。
- 通过渠道注册表选择支付渠道：当前包含余额支付与开发用模拟渠道。
- 校验渠道回调签名与金额，重复回调不重复完成状态变更。
- 支付成功后调用订单服务内部接口确认支付事实。
- 为订单退款提供退款事实查询与退款执行，并回写支付单。
- 使用 `order_payment_gate` 约束同一订单的进行中支付与关闭顺序。

**边界：** 余额账户与余额流水归 `user-service`；支付服务只编排扣款，不直接改用户余额表。模拟渠道不代表真实微信/支付宝接入，也不代表完成外部资金清算。

## 支付流程

1. 订单服务或客户端请求创建支付，支付服务读取订单应付金额。
2. 创建或复用进行中的支付单：同一订单至多一个活跃支付单，靠 `order_payment_gate` 与唯一约束保证。
3. 按渠道执行支付：余额渠道调用用户服务扣款，模拟渠道返回可支付的开发页面。
4. 渠道回调进入 `POST /internal/payments/notify`，校验签名与金额。
5. 支付单状态条件更新为成功，记录 `success_time`、`paid_amount`、`channel_trade_no`。
6. 调用订单服务 `PATCH /internal/orders/{orderId}/paid` 确认订单支付事实；订单侧幂等，重复通知不会重复写销量事件。
7. 回调失败保留支付记录，按业务标识查询核实，不重复创建支付来猜测结果。

## 支付门闩与关闭

- `PaymentOrderGateService.whileOpen(orderId, action)` 在订单支付门闩未关闭时执行操作，避免关闭与支付并发穿透。
- `beginClose(orderId)` 先关闭门闩，再执行关闭支付单。
- 订单取消或超时后，订单服务调用内部关闭接口；已支付订单不能按关闭处理。

## 退款

- 订单服务先查退款事实（`GET /internal/payments/orders/{orderId}/refund-fact`），核对可退金额。
- 退款执行 `POST /internal/payments/orders/{orderId}/refund`，支付服务按原支付单渠道退款并记录结果。
- 退款必须幂等：同一退款业务标识重复请求返回已有结果，不能重复出款。
- 退款完成后订单服务回补库存并推进退款状态。

## 支付渠道

| 渠道 | 说明 |
|---|---|
| `BALANCE` 余额 | 调用用户服务余额扣款，支付密码与余额流水由用户服务保证 |
| 模拟渠道 | 开发环境使用，提供收银台查询与确认支付接口，不代表真实第三方支付 |

渠道实现通过 `PaymentChannelRegistry` 注册，新增渠道需实现 `PaymentChannel` 并配置渠道参数。回调签名工具为 `NotifySignatureUtils`。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `POST /payments` | 创建支付 |
| `GET /payments/{paymentId}` | 查询支付单 |
| `GET /payments/order/{orderId}` | 按订单查询支付单 |
| `POST /internal/payments/notify` | 渠道回调 |
| `GET /internal/payments/orders/{orderId}/refund-fact` | 退款事实查询 |
| `POST /internal/payments/orders/{orderId}/refund` | 执行退款 |
| `GET /mock/channel/checkout/{paymentNo}` | 模拟收银台 |
| `POST /mock/channel/pay/{paymentNo}` | 模拟确认支付 |

支付回调与退款接口不属于公开网关路由，只允许内部调用或渠道回调地址访问。

## 数据表

| 表 | 作用 |
|---|---|
| `payments` | 支付单：支付单号、订单号、用户、渠道、应付/实付金额、渠道流水号、状态、失败原因、回调次数、成功时间、活跃标记 |
| `order_payment_gate` | 订单支付门闩：限制同一订单进行中的支付与关闭顺序 |

`payments` 关键字段：

| 字段 | 说明 |
|---|---|
| `payment_no` | 对外唯一支付单号 |
| `order_id` / `order_no` | 关联订单与订单号快照 |
| `user_id` | 付款用户 |
| `channel_code` | `BALANCE` 等渠道编码 |
| `amount` / `paid_amount` | 应付快照 / 实付金额 |
| `channel_trade_no` | 渠道交易流水号 |
| `status` | `CREATED` / `SUCCESS` / `FAILED` / `CLOSED` |
| `success_time` | 支付成功确认时间，订单支付时间来源 |
| `active_flag` | 进行中支付单标记 |

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`PaymentController.java`](src/main/java/com/hbue/ordering/payment/controller/PaymentController.java) | 支付创建与查询 |
| [`PaymentNotifyController.java`](src/main/java/com/hbue/ordering/payment/controller/PaymentNotifyController.java) | 渠道回调 |
| [`PaymentServiceImpl.java`](src/main/java/com/hbue/ordering/payment/service/impl/PaymentServiceImpl.java) | 支付编排 |
| [`PaymentOrderGateServiceImpl.java`](src/main/java/com/hbue/ordering/payment/service/impl/PaymentOrderGateServiceImpl.java) | 订单支付门闩 |
| [`PaymentRefundServiceImpl.java`](src/main/java/com/hbue/ordering/payment/service/impl/PaymentRefundServiceImpl.java) | 退款 |
| [`BalancePaymentChannel.java`](src/main/java/com/hbue/ordering/payment/channel/BalancePaymentChannel.java) | 余额渠道 |
| [`MockPaymentChannel.java`](src/main/java/com/hbue/ordering/payment/channel/MockPaymentChannel.java) | 模拟渠道 |
| [`PaymentChannelRegistry.java`](src/main/java/com/hbue/ordering/payment/channel/PaymentChannelRegistry.java) | 渠道注册表 |
| [`NotifySignatureUtils.java`](src/main/java/com/hbue/ordering/payment/util/NotifySignatureUtils.java) | 回调验签 |

## 配置与排查

- 渠道参数在 `payment.channel.*`；模拟渠道仅用于开发，生产环境必须关闭或替换。
- 回调地址需要能被渠道访问；本地联调使用模拟渠道页面完成。
- 排查支付问题按顺序核对：支付单状态 → `success_time` → 订单状态与 `paid_at` → 余额流水 → outbox。
- HTTP 超时不等于支付失败；先查询支付单与订单事实，再决定重试。
- 迁移脚本位于 [`docs/database/migrations/payment_db`](../docs/database/migrations/payment_db)。
