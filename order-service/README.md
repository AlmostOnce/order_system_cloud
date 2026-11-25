# order-service 订单服务

> 订单全生命周期：创建（Seata AT 全局事务发起者）、支付确认推进、超时关单、取餐码核销、按菜品明细的退款售后、6 类 Outbox 事件发布。默认端口 `9091`，数据库 `order_db`。不直接访问券库/库存库，跨服务动作全部通过服务契约在全局事务内完成。

## 模块定位与职责边界

- 拥有订单事实：状态机、金额与优惠快照、取餐码、退款轨迹、幂等键。
- 不直接扣余额、不直接改库存表、不直接核销优惠券：分别经支付、库存、优惠券服务完成。
- `order-api` 为对外 Feign 契约包（订单查询/支付确认/销量快照/券核销回执/聊天上下文/推荐购买快照等），供支付、统计、优惠券、聊天、推荐服务使用。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot 3 / Security / OAuth2 Resource Server（RSA JWT + Redis 黑名单） |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Sentinel（降级规则 Data-ID `order-service-degrade-rules`）、**Seata AT**（TM 角色，tx-service-group `order-system-tx-group`，TC `127.0.0.1:9109`） |
| 数据 | MyBatis-Plus 3.5.8 + Druid（max-active=10）+ MySQL `order_db`（默认 `127.0.0.1:9095`，账号 `order_user`） |
| 消息 | Spring Cloud Stream RocketMQ：5 个生产 binding + 2 个自消费 binding |
| 并发 | Redisson（排行投影重建锁）、Redis 取餐码限流 |

- profile 组 `order-timeout = ranking-mq + order-timeout-mq + coupon-mq`；Feign connect 2000 / read 3000。
- 三个事件发布器默认每 1 秒扫描 Outbox，同步发送 3 秒超时；各事件域均有独立开关（关闭时 Outbox 照常记录，恢复后补发）。
- 双共享令牌：`payment.internal.order-token`（支付服务）与 `internal.scope.token`（用户/优惠券/商品等）。

## 代码结构与分层

| 包 / 类 | 职责 |
|---|---|
| `OrderController` | 下单、列表、详情、取消、商家状态推进 |
| `OrderRefundController` | 退款申请/查询/申诉/撤回、商家退款队列与裁决 |
| `OrderPickupController` | 取餐码查询与核销 |
| `OrderServiceImpl`（核心） | 幂等、报价编排、锁券与分摊、扣减、落库、取消、支付推进 |
| `OrderPersistenceServiceImpl` | 落库、乐观锁状态更新、取餐码持久化 |
| `RefundRules` / `OrderRefundServiceImpl` / `OrderRefundTask` | 纯退款规则、退款业务、到期自动审批与执行 |
| `OrderPickupServiceImpl` | 取餐码生成、限流、常量时间比较、核销 |
| `OrderTimeoutOutboxService` / `OrderTimeoutMessaging` / `OrderTimeoutService` | 超时事件记录、发布、消费取消 |
| `SalesOutboxWriter/Publisher`、`OrderNotificationOutboxWriter/Publisher`、`CouponConsumeOutboxWriter/Publisher`、`RecommendationPurchaseOutboxWriter/Publisher` | 四类事件 Outbox 与发布 |
| `CancellationNotificationTcc(Impl/Transactions)` | 取消通知 Seata TCC 分支 |
| `SalesRankingService` / `SalesRankingMessaging` / `SalesRankingJobs` | 自消费销量事件的排行投影与对账 |
| `OrderAccountDeletionService` | 注销阻断计数（未完成订单） |
| `DefaultDataScopeService` / `InternalTokenVerifier` | 商家数据范围、内部令牌校验 |
| `PaymentCloseClient` / `PaymentRefundClient` / `StatisticsRankingClient` | 支付关单、退款资金、旧版排行契约 |
| `order-api` | 契约与降级工厂（`OrderServiceClientFallbackFactory` 等） |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/order_db/`（V1–V11，含两个并列 V10）。

### orders（订单表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| order_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 订单内部主键 |
| order_no | VARCHAR(32) | NOT NULL | uk_orders_order_no | 订单号 |
| user_id | BIGINT | NOT NULL | uk_orders_user_idempotency_key；三枚用户索引首列 | 下单用户 |
| window_id | BIGINT | NOT NULL | — | 档口 ID |
| total_amount | DECIMAL(10,2) | NOT NULL | — | 订单总金额 |
| status | VARCHAR(32) | NOT NULL | 状态索引 | 待支付/已支付/制作中/待取货/已完成/已取消/已退款 |
| remark | VARCHAR(255) | 可空 | — | 订单备注 |
| pickup_code | VARCHAR(32) | 可空 | — | 取餐码（原始列，终态保留便于追溯） |
| idempotency_key | VARCHAR(128) | 可空 | uk_orders_user_idempotency_key | 下单幂等键 |
| request_hash | CHAR(64) | 可空 | — | 请求内容哈希，同键不同内容拒绝 |
| active_pickup_code | VARCHAR(32) | VIRTUAL 生成列，终态自动为 NULL | uk_order_active_pickup_code | 活跃订单取餐码，自动随终态释放，仅用于唯一约束与核销定位 |
| paid_at | DATETIME | 可空 | — | 支付首次成功时间，来源于支付服务 success_time，UTC+8 |
| expires_at | DATETIME | 可空 | — | 创建时固定的支付截止时间 |
| completed_at | DATETIME | 可空 | — | 首次取餐完成时间，售后 72 小时期限依据 |
| user_coupon_id | BIGINT UNSIGNED | 可空 | — | 下单锁定的用户券 ID，不建跨库外键 |
| coupon_title_snapshot | VARCHAR(120) | 可空 | — | 下单时优惠券标题快照 |
| discount_amount | DECIMAL(12,2) | NOT NULL，默认 0 | — | 订单优惠总额快照 |
| payable_amount | DECIMAL(12,2) | NOT NULL，默认 0 | — | 订单应付金额快照 |
| version | INT | NOT NULL，默认 0 | — | 乐观锁版本号 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | 用户创建时间索引 | 创建/更新时间 |

- 唯一键：`uk_orders_order_no`、`uk_orders_user_idempotency_key(user_id, idempotency_key)`、`uk_order_active_pickup_code`。
- 索引：`(user_id, created_at)`、`(user_id, order_id)`（本人游标分页）、`(user_id, status, order_id)`（本人状态筛选分页）。

### order_items（订单明细表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| item_id | BIGINT | NOT NULL，自增 | 主键 | 明细主键 |
| order_id | BIGINT UNSIGNED | NOT NULL | 外键 fk_order_items_order；订单索引 | 所属订单 |
| product_id | BIGINT | NOT NULL | 商品索引 | 菜品 ID |
| product_code_snapshot | VARCHAR(64) | NOT NULL | — | 菜品编码快照 |
| product_name_snapshot | VARCHAR(128) | NOT NULL | — | 菜品名称快照 |
| unit_price_snapshot | DECIMAL(10,2) | NOT NULL | CHECK ≥0 | 下单时单价快照 |
| quantity | INT UNSIGNED | NOT NULL | CHECK >0 | 购买数量 |
| line_amount | DECIMAL(12,2) | NOT NULL | CHECK ≥0 | 行金额 |
| selection_key_snapshot | VARCHAR(256) | NOT NULL，默认 'base' | — | 规格选项 ID 规范化组合键，无规格为 base |
| specification_text_snapshot | VARCHAR(1024) | NOT NULL，默认 '' | — | 规格组和选项文字快照 |
| specification_selection_snapshot | JSON | NOT NULL，默认 JSON_ARRAY() | — | 结构化规格快照，旧订单未知为 [] |
| line_discount_amount | DECIMAL(12,2) | NOT NULL，默认 0 | — | 本明细分摊的优惠金额 |
| line_payable_amount | DECIMAL(12,2) | NOT NULL，默认 0 | — | 本明细优惠后实付金额 |
| refund_reserved_quantity | INT | NOT NULL，默认 0 | CHECK 链 | 已申请且未关闭的数量，含已退款，防重复申请 |
| refund_approved_quantity | INT | NOT NULL，默认 0 | CHECK 链 | 已批准（含处理中/已退款）数量，不再参与履约 |
| refunded_quantity | INT | NOT NULL，默认 0 | CHECK 链 | 已确认退款与库存步骤完成的数量 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 创建时间 |

- CHECK：`chk_item_refund_quantities`（已退 ≤ 已批 ≤ 已占 ≤ 购买数量）及单价/数量/金额约束。

### order_refunds（退款单与执行任务表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| refund_id | BIGINT | NOT NULL，自增 | 主键 | 退款主键 |
| refund_no | CHAR(32) | NOT NULL | uk_refund_no | 稳定退款号，跨服务幂等依据 |
| request_key | VARCHAR(64) | NOT NULL | uk_refund_request | 学生申请幂等键 |
| order_id | BIGINT | NOT NULL | 订单/窗口队列索引 | 订单主键 |
| user_id | BIGINT | NOT NULL | uk_refund_request 首列 | 订单所属学生 |
| item_id | BIGINT | NOT NULL | — | 订单明细主键 |
| product_id | BIGINT | NOT NULL | — | 菜品主键 |
| product_name | VARCHAR(128) | NOT NULL | — | 菜品名称快照 |
| quantity | INT | NOT NULL | CHECK >0 | 本次申请数量 |
| amount | DECIMAL(10,2) | NOT NULL | CHECK >0 | 服务端按实付明细分摊计算的金额 |
| reason | VARCHAR(500) | NOT NULL | — | 申请原因 |
| status | VARCHAR(24) | NOT NULL | 截止/重试/窗口队列索引 | PENDING/REJECTED/APPEALED/APPROVED/PROCESSING/SUCCESS/CLOSED |
| original_status | VARCHAR(32) | NOT NULL | — | 申请时订单状态 |
| restock | TINYINT | NOT NULL，默认 0 | — | 批准后是否回库存，完成订单恒为 0 |
| decision_reason | VARCHAR(500) | 可空 | — | 最近处理说明 |
| decided_by | BIGINT | 可空 | — | 最近审批人，系统自动审批为空 |
| due_at | DATETIME | NOT NULL | 截止索引 | 商家处理截止，申请后 72 小时 |
| next_attempt_at | DATETIME | NOT NULL，默认当前时间 | 重试索引 | 下次执行或租约到期时间 |
| attempts | INT | NOT NULL，默认 0 | — | 累计执行次数 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 申请/更新时间 |

- 唯一键：`uk_refund_no`、`uk_refund_request(user_id, request_key)`；索引：`(order_id, refund_id)`、`(status, due_at)`、`(status, next_attempt_at, refund_id)`、`(status, order_id, refund_id)`。

### order_refund_actions（退款审批轨迹）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| action_id | BIGINT | NOT NULL，自增 | 主键 | 处理轨迹主键 |
| refund_id | BIGINT | NOT NULL | 轨迹索引首列 | 退款主键 |
| actor_id | BIGINT | 可空 | — | 操作者，系统为空 |
| action | VARCHAR(32) | NOT NULL | — | 申请/批准/拒绝/介入/关闭/到账 |
| detail | VARCHAR(500) | NOT NULL | — | 原因或处理说明 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 处理时间 |

- 索引：`idx_refund_actions(refund_id, action_id)`，轨迹不可覆盖。

### 事件队列六表

**sales_event_outbox（销量事件）**：主键 `event_id`（VARCHAR 80）；`order_id`（唯一键 uk_sales_event_order + 外键）；`payload` JSON；`attempts`、`next_attempt_at`、`published_at`、`last_error`、`created_at`；待发布索引 `(published_at, next_attempt_at)`。每订单一条销量事件。

**order_notification_outbox（通知事件）**：自增主键；`event_id` 唯一键；`order_id + order_version` 唯一键（uk_order_notification_version，同版本只发一次）；`payload` JSON；重试字段同上；待发布索引 `(published_at, next_attempt_at, id)`。

**order_notification_tcc（取消通知 TCC 状态）**：主键 `xid`；`order_id`、`order_version`、`recipient_id`；`status` PREPARED/CONFIRMED/CANCELLED（保留取消墓碑防空回滚后 Try 悬挂）；时间戳自动维护；索引 `(status, updated_at)`。

**order_timeout_outbox（超时事件）**：主键 `event_id`；`order_id`、`expires_at`（毫秒时间戳）；重试字段 + `dead_letter_at`（进入死信待人工处理时间）+ 脱敏 `last_error`；待发布索引 `(published_at, next_attempt_at)`。

**order_coupon_consume_outbox（券核销事件）**：主键 `event_id`；`order_no` 唯一键（一订单一条）；`user_coupon_id`；`payload` MEDIUMTEXT；`status` PENDING/PUBLISHED/CONFIRMED；`published_at`（进 Broker 时间）与 `confirmed_at`（券服务核销确认时间）分离；重试字段；待发布索引 `(status, next_attempt_at, event_id)`。

**recommendation_purchase_outbox（推荐购买快照）**：主键 `event_id`（recommendation-purchase:订单ID:源版本）；`order_id + order_version` 唯一键（一版订单最多一份不可变快照）+ 外键；`payload` JSON 含全部规格明细与退款份数；重试字段；待发布索引 `(published_at, next_attempt_at)`。

### undo_log（Seata AT 回滚日志）

官方标准结构：主键 `branch_id`、`xid` 唯一键（ux_undo_log）、`context`、`rollback_info` LONGBLOB、`log_status`（0 正常/1 已防御）、`log_created/log_modified`。支撑下单/取消全局事务在 order_db 上的本地分支回滚。

## 订单状态机

| 状态 | 含义 | 允许流转 |
|---|---|---|
| 待支付 | 等待支付 | → 已支付、已取消 |
| 已支付 | 等待商家接单 | → 制作中 |
| 制作中 | 商家制作 | → 待取货 |
| 待取货 | 等待取餐 | → 已完成（仅取餐码核销） |
| 已完成 | 结束 | 无（72 小时内可退款） |
| 已取消 | 未支付取消 | 无 |
| 已退款 | 未完成订单全额退款 | 无 |

商家推进受限：不能从待支付出发、不能直接置取消或完成；所有状态更新使用原状态条件更新 + 乐观锁，避免支付与取消并发时双输赢。

## 核心业务流程

### 创建（`OrderServiceImpl.createOrder`，`@GlobalTransactional`）

1. 幂等键校验与请求摘要计算；同用户同键重放返回原订单，内容不同返回冲突。
2. 商品服务报价确定价格与规格，不信任客户端金额。
3. 有券则在全局事务内调优惠券服务锁券，失败整单失败；优惠金额按明细分摊。
4. 库存服务扣减（Seata 分支）。
5. 订单、明细、优惠快照本地落库。

### 取消与超时

- 顾客取消 / 到期取消均为全局事务（`cancel-order-restore-stock` / `expire-order-restore-stock`）：释放优惠券、库存回补走 Seata 分支。
- 取消通知使用 TCC 分支：状态提交则通知必发、回滚则不写（`CancellationNotificationTccImpl.prepare/confirm/cancel`）。
- 超时事件经 Outbox 发布 `order-payment-timeout-v1` 后自消费，按状态条件取消并回补；死信 `%DLQ%` 由观察组标记待人工。

### 支付推进

支付服务回调内部接口 → 乐观锁置已支付并写支付时间 → 同事务写销量、通知、券核销、推荐四类 Outbox。支付金额只按应付额。

### 取餐码

- 顾客首次读取时生成六位取餐码并持久化；生成列 + 唯一索引保证进行中订单取餐码唯一，碰撞重生成，完成或取消后释放。
- 核销：仅待取货；商家数据范围校验；每商家每分钟 30 次尝试限流（错误与越权也计数）；常量时间比较；乐观锁核销 + 审计日志。

### 退款售后

- 可申请状态：已支付/制作中/待取货/已完成；已完成需在完成时间起 72 小时内。
- 金额按明细实付分摊（最后剩余份数承接余分），不采信前端金额；同一明细处理中申请占用额度，组合不超实付。
- 流程：申请 → 商家批准/拒绝 → 顾客申诉 → 管理裁决 → 执行。到期任务自动审批与执行（`OrderRefundTask`）：先租约抢占 → 退资金 → 需要时回补库存 → 完成；任一步未确认绝不标记成功，同退款号幂等重试。

### 订单分页与游标契约

- 状态筛选 `status` 取值 all/pending/active/completed/cancelled，默认 all，未知值拒绝；active 由服务端映射为已支付/制作中/待取货三种履约中状态。
- `limit` 默认 20、范围 1–50；数据层最多读取 limit+1 条判断 hasMore，响应结构 `{items, nextCursor, hasMore}`，不做全量 COUNT 与深 OFFSET，本页只转摘要 VO、不加载菜品明细。
- 游标必须是本人合法订单编号：只校验归属，不要求状态仍与当前筛选一致（状态流转后仍可继续翻页）；切换筛选必须从首屏重新开始。
- 窗口名称由 `OrderWindowNameService` 按页批量获取（一页 ≤50 条只发一次批量查询），商品服务不可用时沿用名称缺失降级，不影响订单列表。
- 前端契约：追加按 orderId 去重；切换筛选、离页或账号切换使旧请求失效；追加失败保留列表与游标下次触底重试；新页服务端时间只用于校准时钟偏移，不重置已加载订单的支付倒计时。

### 超时关单实现细节

- Broker 延迟等级共 19 级（原 18 级 + 追加第 19 级 15 分钟）；发送按「截止时间 − 当前时间」选取不超过剩余时间的最大等级，剩余不足 1 秒按 1 秒，已过期事件立即发送。
- 消费者先核对数据库真实剩余时间：未到期则续投下一条延迟消息后再确认当前消息；到期才执行状态条件取消与库存回补。
- Outbox 待发送扫描每轮最多 50 条；事件 ID 格式 `order-timeout-v1:{orderId}`。
- 死信人工补偿流程：清空对应 event_id 的 `published_at` 与 `dead_letter_at`、将 `next_attempt_at` 置为当前时间重发原事件；只重放消息，不手工 UPDATE 订单与库存。日志标记：发送失败 `ORDER_TIMEOUT_PUBLISH_RETRY`、进入死信 `ORDER_TIMEOUT_DEAD_LETTER`。
- API 契约：客户端与支付内部视图返回 `paymentExpiresAt`/`serverTime` 毫秒时间戳，页面只做展示，是否可支付由服务端判定。
- 自动超时取消的库存回补：操作者为空、备注标明「系统超时取消」，库存业务引用仍为原订单号，不伪记顾客手动操作。十五分钟是支付期限，不是故障场景下恰好第 900 秒回补的承诺。

### 退款执行与管理细则

- 商家 72 小时未处理自动同意；已接单餐品不自动回库。经理介入（APPEALED）无额外自动审批期限。
- 到期自动审批任务每 30 秒扫描，每轮最多 50 笔 PENDING；退款执行任务每 5 秒有界读取最多 10 笔 APPROVED 或租约到期的 PROCESSING，执行租约 60 秒。
- 并发规则：订单内所有变更先锁订单行再锁退款单行，与接单、出餐、核销共享订单版本条件；相同审批结果可重试，不能覆盖另一个决定。
- 管理队列接口每页 20 条，非平台总经理必须指定并验证授权窗口；裁决权限：店主只处理 PENDING、经理只处理 APPEALED；已完成订单不能 `restock=true`，拒绝退款也不回库。
- 学生申请幂等键 8–64 位（字母/数字/下划线/短横线）；退款列表默认最新 20 条、游标限同一本人订单；明细返回 itemId/refundableQuantity/fulfillableQuantity/refundedQuantity。
- 履约边界：整单全部份数获批退款后不能再接单、出餐或凭旧取餐码核销；此前已完成的订单保持「已完成」与既有评价。
- 退款核验契约（用户服务侧）：锁定账户行 → 核验原 PAY 扣款流水及同支付单累计退款上限 → 余额与 REFUND 流水同事务入账；账户停用/软删除后已入账的重试仍确认原事实，未入账且账户不可用则保留失败待处理。
- 订单聊天上下文的 24 小时可发送期限以首次 `completed_at` 为依据，避免退款等后续更新延长期限。

### 销量口径（随事件约定）

销量事件只累计成功支付订单的菜品原始购买份数，退款不冲减历史销量；月份按支付成功时间（Asia/Shanghai 自然月）归属；统计按下单时的菜品 ID、名称、窗口、份数快照，同一菜品跨窗口分别计数；旧版 V1 事件仅有订单号时由统计服务经内部只读接口回读快照。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 下单请求重复提交 | 用户维度幂等键唯一键 `(user_id, idempotency_key)` + 请求内容 SHA-256 摘要：同键同内容回查返回原订单；同键不同内容直接拒绝 | 幂等重放返回首次结果；内容冲突明确报错 |
| 支付确认与顾客取消并发 | 状态推进一律「原状态条件更新 + 乐观锁 version」：只有条件命中的赢家生效，避免取消与支付双输赢 | 败者收到状态冲突错误，不再重试旧动作 |
| 取餐码碰撞 | `active_pickup_code` 生成列 + 唯一索引：进行中订单取餐码唯一；碰撞时重新生成重试，完成/取消后释放复用 | 生成重试失败按持久化失败处理，不落半状态 |
| 取餐码核销竞争/暴力尝试 | 核销乐观锁（待取货条件更新）唯一赢家；每商家每分钟 30 次尝试限流（Redis，错误与越权也计数）；常量时间比较防时序侧信道 | 超限拒绝；核销写审计日志；失败不泄露码是否接近正确 |
| 全局事务回滚 | 下单/取消为 Seata 发起者：锁券（券库）、扣库存（库存库分支）任一失败，undo_log 镜像回滚全部分支 | 整单失败，用户看到创建失败，无跨库残留 |
| 取消通知的确定投递 | 取消通知走 Seata TCC 分支：Try 预写 PREPARED、Confirm 落发布、Cancel 留墓碑；TCC 栅栏独立于 AT 订单行 | 回滚则通知必不发布；空回滚由墓碑防悬挂 |
| 退款执行任务多实例竞争 | 到期任务先以条件更新抢占租约（next_attempt_at/attempts），抢到才执行；执行链「退资金 → 回库存 → 置成功」，每步按退款号幂等 | 未抢到租约的任务本轮跳过；任一步未确认绝不标记成功，同号重试安全 |
| 退款与履约并发 | 明细三级计数（已占 ≥ 已批 ≥ 已退）+ CHECK 链；申请占用额度，组合不超实付 | 超额申请被数据库约束拒绝 |
| 事件重复发布 | 六张 Outbox 表各自唯一键（订单号/事件 ID/订单版本）保证一事件一条；发布器同步发送确认后才标记 published_at | 发布失败指数退避重试；消费端再以事件 ID 幂等吸收 |
| 超时消息与死信 | 本地不重试，交 Broker 重投 16 次后进死信；死信由观察组标记 `dead_letter_at` 待人工 | 超时取消仍可由顾客手动取消兜底 |

## 接口清单

顾客端（CUSTOMER）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/orders` | 创建订单（幂等键） |
| GET | `/orders/list`、`/orders/{orderId}` | 列表与详情 |
| PATCH | `/orders/{orderId}/cancel` | 取消待支付订单 |
| GET | `/orders/{orderId}/pickup-code` | 本人取餐码 |
| POST/GET | `/orders/{orderId}/refunds` | 退款申请/查询 |
| POST | `/orders/{orderId}/refunds/{refundId}/appeal`、`/withdraw` | 申诉、撤回 |
| GET | `/orders/rankings/sales` | 销量榜（旧入口，现由统计服务直达） |

商家端（STALL_MANAGER / RESTAURANT_MANAGER，退款裁决含 GENERAL_MANAGER）：状态推进、取餐核销（`POST /orders/pickup/verify`）、退款队列与裁决。

内部接口（`/internal/**`，`X-Internal-Token`，网关不暴露）：订单查询、支付确认、销量快照、券核销回执、聊天上下文、推荐购买快照、注销阻断计数。

## 消息、异步与缓存设计

| topic | 方向 | 消费方 |
|---|---|---|
| `order-paid-sales-v2` | 生产 | 统计服务（+ 本服务排行投影自消费） |
| `recommendation-purchase-snapshot-v1` | 生产 | 推荐服务 |
| `order-status-notification-v1` | 生产 | 通知服务 |
| `order-payment-timeout-v1` | 生产 + 自消费 | 本服务（死信由观察组消费） |
| `order-coupon-paid-v1` | 生产 | 优惠券服务（核销后回执确认 Outbox） |

- 全部 Outbox + 同步发送确认；Broker 故障只推迟投递不阻塞业务事务。
- 幂等靠 Outbox 唯一键与事件 ID；超时消息本地不重试，交 Broker 重投 16 次后进死信留痕。

## 安全设计

JWT 验签 + 黑名单 fail-closed；内部接口共享令牌，无有效令牌直接 401；商家操作经数据范围服务收敛（可操作窗口判定）；核销审计独立记录。

## 配置项清单

| 配置 | 说明 |
|---|---|
| `order.payment-timeout.*` | 超时关单开关与发布间隔 |
| `ranking.* / recommendation.* / notification.order-events.* / notification.cancel-tcc.enabled / coupon.consume.messaging.enabled` | 各事件域开关（关闭时 Outbox 照记，恢复补发） |
| `SEATA_*` / `ROCKETMQ_NAMESERVER` | 事务与 MQ 地址 |
| `payment.internal.order-token` / `internal.scope.token` | 双共享令牌 |
| Sentinel 降级规则 | Nacos Data-ID `order-service-degrade-rules` |

## 异常与失败行为

- 下单/取消任一分支失败 → Seata 全局回滚（含 undo_log 镜像）；券服务降级工厂 fail-closed 返回不可用。
- 支付推进失败由支付服务留痕重试；退款执行「先资金后库存」，未确认不置成功。
- 取餐码限流、超时死信、审计失败均只留痕不阻塞主流程。

## 关键代码入口

`OrderServiceImpl`（创建与取消全局事务）→ `OrderRefundServiceImpl` + `RefundRules`（退款规则）→ `OrderPickupServiceImpl`（核销安全）→ 六个 Outbox Writer/Publisher（事件范式）→ `order_db` 迁移脚本。

## 已知限制与未验证项

- 取消通知 TCC 方案为待验收设计。
- 排行投影自消费与统计服务榜并存，旧排行入口为兼容保留。
- 全链路压测与多实例并发验证未完成。

## 关联文档

- `docs/database/migrations/order_db/`：建表与变更脚本。
- `payment-service/README.md`、`coupon-service/README.md`、`stock-service/README.md`：全局事务协作方。
- 根 README「跨模块业务链路」：下单 → 支付 → 履约 → 售后链路。
