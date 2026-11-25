# payment-service 支付服务

> 支付单生命周期：创建、渠道编排、回调验签、余额同步结算、关单编排、退款事实与退款入账。默认端口 `9101`，数据库 `payment_db`。不接入 MQ，与订单/用户的联动全部为同步 Feign + 幂等条件更新；支付成功事实永不回滚。

## 模块定位与职责边界

- 拥有支付单事实：状态机、金额快照、渠道交易号、回调次数。
- 账务不归本服务：余额扣减与流水在用户服务同库事务完成；订单推进在订单服务完成。本服务不实现跨服务分布式事务——学习项目明确不做自动退款与跨服务补偿，失败留 ERROR 日志人工核查。
- 无退款表：退款事实不落支付库，原支付单保持 SUCCESS，退款入账权威在用户服务流水。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）、Actuator |
| 数据 | MyBatis-Plus 3.5.8 + Druid + MySQL `payment_db`（默认 `127.0.0.1:9095`，max-active=10） |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer + **feign-okhttp**（HttpURLConnection 不支持 PATCH） |
| 共享模块 | common-web、common-redis、user-api、order-api |

- Redis 仅用于 JWT 注销黑名单；无 MQ 依赖、无 WebSocket。
- 渠道配置 `payment.channel.*`：HMAC 密钥、回调地址、收银台地址、自动确认开关（默认关）与延迟。
- 双内部令牌：`payment.internal.order-token`（订单服务）与 `internal.scope.token`（用户服务）。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `PaymentServiceImpl` | 核心：创建支付单、余额结算、回调处理、关单编排 |
| `PaymentRefundServiceImpl` | 退款事实查询 + 退款入账（仅余额渠道） |
| `PaymentOrderGateServiceImpl` | 支付单创建/回调与关单的持久互斥门锁 |
| `PaymentChannel` / `PaymentChannelRegistry` | 渠道策略接口与按渠道码路由 |
| `MockPaymentChannel` | 模拟异步回调渠道（HMAC 下单与验签） |
| `BalancePaymentChannel` | 同步结算渠道（无回调） |
| `MockChannelNotifier` | 虚拟线程异步回调，最多 3 次重试 |
| `NotifySignatureUtils` | HMAC-SHA256 规范串验签，300 秒时间窗 + 常量时间比较 |
| `PaymentController` | 学生端支付单创建与查询 |
| `PaymentNotifyController` / `PaymentCloseInternalController` / `PaymentRefundInternalController` | 渠道回调与内部关单/退款接口 |
| `OrderInternalClient` | 订单内部调用（刻意不配 fallback：推进失败必须显式留痕） |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/payment_db/V1`（支付单）、`V2`（订单支付门锁）。

### payments（支付单）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| payment_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 支付单主键 |
| payment_no | VARCHAR(32) | NOT NULL | uk_payments_payment_no | 业务支付单号 |
| order_id | BIGINT | NOT NULL | uk_payments_order_active；idx_payments_order | 订单 ID，跨库逻辑关联，不设外键 |
| order_no | VARCHAR(32) | NOT NULL | — | 订单号快照，便于排查 |
| user_id | BIGINT | NOT NULL | 用户创建时间索引首列 | 付款用户快照，归属校验与对账用 |
| channel_code | VARCHAR(32) | NOT NULL，默认 'MOCK' | — | 支付渠道标识 |
| amount | DECIMAL(10,2) | NOT NULL | — | 应付金额，创建时的订单快照，绝不取客户端输入 |
| paid_amount | DECIMAL(10,2) | 可空 | — | 实收金额，回调成功时写入 |
| channel_trade_no | VARCHAR(64) | 可空 | — | 渠道交易号 |
| status | VARCHAR(16) | NOT NULL，默认 'CREATED' | — | CREATED/SUCCESS/FAILED/CLOSED |
| fail_reason | VARCHAR(255) | 可空 | — | 失败原因或异常备注 |
| notify_count | INT | NOT NULL，默认 0 | — | 累计收到回调次数 |
| last_notify_at | DATETIME | 可空 | — | 最近一次回调时间 |
| success_time | DATETIME | 可空 | — | 支付成功时间 |
| active_flag | TINYINT | VIRTUAL 生成列 `status='CREATED' ? 1 : NULL` | uk_payments_order_active | 进行中标记：NULL 不参与唯一比较，实现同订单最多一个 CREATED 支付单 |
| created_at | DATETIME | NOT NULL | 用户创建时间索引末列 | 创建时间 |
| updated_at | DATETIME | NOT NULL | — | 更新时间 |

- 唯一键：`uk_payments_payment_no(payment_no)`、`uk_payments_order_active(order_id, active_flag)`；普通索引：`idx_payments_user_created(user_id, created_at)`、`idx_payments_order(order_id)`。

### order_payment_gate（支付与关单互斥门锁）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| order_id | BIGINT | NOT NULL | 主键（非自增） | 订单内部编号 |
| closing | TINYINT | NOT NULL，默认 0 | — | 是否已开始关闭支付入口 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 创建时间 |

**状态机**：CREATED → SUCCESS / FAILED / CLOSED；三个终态不可再流转。

**渠道**：`MOCK`（模拟异步回调）、`BALANCE`（同步结算、无回调）；默认 MOCK。

## 核心业务流程

### 创建支付单

1. 路由渠道 → 持内部令牌查询订单并校验归属（非本人按不存在处理）。
2. 已有 SUCCESS 支付单则补推订单已支付（修复丢失确认）。
3. 门锁事务内校验：订单必须待支付且未超支付截止时间；复用活动支付单且渠道必须一致；新建时唯一键兜底并发，冲突方回查复用。
4. 余额渠道立即同步结算；其他渠道调渠道下单返回收银台凭证。

### 余额支付

1. 调用户服务余额扣减（业务引用 = 支付号，幂等防二次扣款；携带支付密码）。
2. 扣款成功后本地条件更新 CREATED→SUCCESS，仅行数=1 的赢家推进订单。
3. 扣款失败按下游错误映射，支付单保持 CREATED 可重试；重试复用同一支付号。

### 渠道回调

1. 先查单后验签（验签密钥由支付单渠道决定）；验签失败 401。
2. 无条件累计回调次数（观察重放）。
3. 成功通知：金额比对（实收≠应付 409）→ 门锁 + 条件更新 CREATED→SUCCESS → 赢家推进订单；0 行或门已关时：已有 SUCCESS 则重推订单修复，FAILED/CLOSED 返回成功让渠道停止重试。
4. 失败通知：条件更新置 FAILED。
5. MOCK 渠道成功通知额外校验订单支付期限，迟到直接拒绝。

### 推进订单

回读支付单取成功时间 → Feign 调订单内部已支付接口。订单侧冲突（已取消）或网络失败**不回滚支付成功事实**，只留 ERROR 日志人工核查。

### 支付门槛与关单（订单服务超时调用）

1. `beginClose` 置门锁 closing=1，此后创建与回调一律被拒（「订单支付入口已关闭」）。
2. 遍历该订单全部支付单：已有 SUCCESS 取最早成功时间；余额单先向用户服务关单查询（返回已付则恢复 SUCCESS，已关则置 CLOSED）；MOCK 直接关闭；其他渠道明确报「尚未实现安全关单」。
3. 返回关单结果（已支付时间或已关闭）；**不回调订单服务**，避免与订单 Seata 分支反向等待。

### 退款

- 事实查询：返回最近一笔余额渠道成功支付单的金额与渠道；模拟渠道拒绝（不能凭模拟回调向账户入金）。
- 退款入账：金额 >0、两位小数、不超过实收 → 调用户服务按退款单号入账并校验回执；不包裹跨服务事务，订单退款任务按同一退款单号重试，用户流水是入账权威。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 同一订单并发创建多个支付单 | 虚拟生成列 `active_flag`（仅 CREATED 为 1）+ 唯一键 `(order_id, active_flag)`：并发插入只有一个赢家，失败方回查复用既有支付单；复用校验渠道一致 | 冲突返回 409 语义由回查吸收；渠道不一致明确拒绝 |
| 创建支付单与关单并发 | `order_payment_gate` 行 `FOR UPDATE` 互斥：创建前检查 closing 标记，关单先 `beginClose` 置 closing=1 形成持久屏障 | 关单后创建/回调返回「订单支付入口已关闭」；回调方据此停止重试 |
| 回调与关单并发竞争状态 | 状态条件更新 `WHERE status='CREATED'`：只有一个赢家；0 行更新时区分三种已终态——已有 SUCCESS 则重推订单修复丢失确认，FAILED/CLOSED 返回成功让渠道停止重试 | 赢家才推进订单；败者不产生第二次推进 |
| 订单被并发推进两次 | 金额比对（实收≠应付 409）+ 订单侧乐观锁推进；支付单内 `notify_count` 无条件累加仅作观察 | 推进失败不回滚支付事实，留 ERROR 日志人工核查 |
| 余额重复扣款 | 扣款业务引用 = 支付号，用户服务侧唯一键幂等；扣款失败重试复用同一支付号，不会新开支付单 | 已入账事实优先：响应丢失后同号重试返回成功 |
| 渠道回调重放 | HMAC 规范串验签 + 300 秒时间窗 + 常量时间比较；验签失败 401 且计数 | 超窗或签名不符拒绝，notify_count 留痕 |
| 退款重复入账 | 退款入账按退款单号在用户服务流水侧幂等；订单侧退款任务按同一退款号重试 | 同号重放校验原支付号与金额后直接返回成功，不二次入账 |
| 关单与余额结算竞态 | 关单时先向用户服务关单查询结算结果：返回已付则恢复 SUCCESS，已关则置 CLOSED；其余结果显式报错 | 不做模糊兜底，结果只有已付或已关两种 |
| 关单请求超时的判定 | 超时必须按结算屏障与既有流水核对结果，不能用「暂时没查到流水」推断不会再扣款 | 查询失败保持未决待重试，不猜测关闭结果 |
| 支付请求与到期关闭并发 | 十五分钟内已获准进入的支付请求与到期关闭按用户库结算屏障的先后顺序决定结果，已扣款请求不被强制当失败处理 | 以屏障顺序为准，输赢双方各得明确结果 |
| 接入真实收款渠道 | 当前 MOCK 无真实资金流；接入真实渠道须另行实现渠道关单、查询与退款协议 | 现有关单编排仅覆盖 MOCK 与余额渠道 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/payments` | CUSTOMER | 创建支付单（订单号、渠道、余额支付需支付密码） |
| GET | `/payments/{paymentId}` | CUSTOMER | 支付单状态（归属校验） |
| GET | `/payments/order/{orderId}` | CUSTOMER | 按订单查最近支付单（前端轮询） |
| POST | `/internal/payments/notify` | 渠道验签 | HMAC 签名回调（网关不暴露） |
| POST | `/internal/payments/orders/{orderId}/close` | X-Internal-Token | 超时关单编排 |
| GET | `/internal/payments/orders/{orderId}/refund-fact` | X-Internal-Token | 退款事实 |
| POST | `/internal/payments/orders/{orderId}/refund` | X-Internal-Token | 退款入账 |
| GET/POST | `/mock/channel/*` | dev profile | 模拟渠道收银台与支付触发（仅本地） |

顾客侧**没有**「标记支付成功」端点——支付状态只能由回调或余额结算驱动。

## 消息、异步与缓存设计

- 不接入 MQ；异步行为仅 Mock 渠道的虚拟线程回调（非 2xx 重试 3 次）。
- 回调幂等：条件更新唯一赢家 + 订单侧乐观锁推进双重防护；签名 300 秒时间窗防重放。

## 安全设计

- 渠道回调、mock、内部路径在过滤链放行但网关不路由，安全性由业务层 HMAC 验签与共享令牌保证。
- JWT 黑名单不可确认时拒绝（fail-closed）；订单归属校验失败统一按不存在处理，不泄露存在性。

## 配置项清单

| 配置 | 说明 |
|---|---|
| `payment.channel.secret / notify-url / checkout-base-url` | 渠道签名与回调地址 |
| `payment.channel.auto-confirm-enabled / auto-confirm-delay-ms` | 自动确认（默认关闭） |
| `payment.internal.order-token` | 订单服务内部令牌 |
| `internal.scope.token` | 用户服务内部令牌 |
| `security.jwt.*` | 验签与黑名单 |

## 异常与失败行为

- 查单失败不创建支付单；订单服务不可用统一服务不可用。
- 支付成功事实永不回滚：推进订单失败留 ERROR 日志人工处置。
- 关单时余额结算结果只能是已付或已关，否则显式报错。
- 并发控制四层：虚拟列唯一键、订单门锁 FOR UPDATE、状态条件更新、用户服务幂等扣款。

## 关键代码入口

`PaymentServiceImpl`（创建/回调/关单全链路）→ `PaymentOrderGateServiceImpl`（互斥门锁）→ `PaymentRefundServiceImpl`（退款协议）→ `payment_db` 迁移脚本。

## 已知限制与未验证项

- 仅实现 MOCK 与 BALANCE 两个渠道，真实支付渠道未接入。
- 自动确认默认关闭；跨服务一致性依赖幂等重试与人工核查，无自动补偿。

## 关联文档

- `user-service/README.md`：余额扣减/退款流水与幂等。
- `order-service/README.md`：支付超时关单与 Seata 链路。
- 根 README「跨模块业务链路」：下单 → 支付 → 推单链路。
