# statistics-service 销量统计服务

> 已支付订单销量的读模型投影服务：MySQL 事实表 + 累计/月度汇总 + Redis ZSet 排行缓存。默认端口 `9111`，数据库 `statistics_db`，消费订单支付销量事件，是全系统唯一销量口径所有者（退款不减少销量），推荐服务只读其内部接口。

## 模块定位与职责边界

- 销量事实的唯一口径：累计榜（ALL）与自然月（MONTH）双周期，按窗口与菜品两级汇总；原始购买份数不因退款减少。
- 对外只输出销量榜与月售数字，不返回订单、用户、支付明细。
- Redis ZSet 榜单是缓存而非事实：版本对账失败即从 MySQL 全量重建。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）、Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Spring Cloud Stream RocketMQ |
| 数据 | MyBatis-Plus 3.5.8（6 张表全部注解 SQL）、Druid、MySQL `statistics_db`（默认 `127.0.0.1:9095`） |
| 缓存 | Redis + Redisson（显式引入 `redisson-spring-boot-starter` 与 `redisson-spring-data-35`，榜单重建依赖其锁实现） |
| 定时任务 | `@EnableScheduling`；榜单对账默认 300 秒 |

- 端口 `9111`（固定）；`spring.profiles.include: statistics-mq`；MQ 消费 binding 均 `max-attempts:1`、`CONSUME_FROM_FIRST_OFFSET`、`max-reconsume-times:16`，并绑定显式重抛错误的 `salesStatisticsRetryError`。
- 会话时区 `+08:00`；JWT 与黑名单配置同其他资源服务（本服务不把 subject 换内部 ID，直接使用验签后的 Jwt）。
- 消息开关 `statistics.messaging.enabled`。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `SalesStatisticsController` | 学生查询端点：销量榜、窗口月售、菜品月售 |
| `SalesStatisticsInternalController` | 内部销量榜接口（`X-Internal-Token`），供推荐服务拉取 |
| `SalesStatisticsService` | 消费编排：V1/V2 事件分流 + 月售查询 |
| `SalesProjectionService` | MySQL 事务投影：事实表 + 汇总表 + 投影版本递增，重复载荷逐字段一致性校验 |
| `SalesRankingCacheService` | Redis ZSet 榜单：Lua 增量/读取/重建 + Redisson 锁 + fencing generation |
| `SalesStatisticsMessaging` | 2 个 Consumer 与显式重抛的错误处理器 |
| `SalesPaidEvent` | V1/V2 事件反序列化契约，校验 eventId=`order-paid-sales:{orderId}` |
| `SalesProjectionMapper` | 全部投影 SQL |
| `OrderSalesInternalClient` | V1 兼容：向订单服务回读支付销量快照 |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/statistics_db/V1`（含从订单/支付库的历史迁移与幂等重算汇总）、`V2`（注释修正）。

### statistics_sales_events（已支付订单销量事实）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | VARCHAR(80) | NOT NULL | 主键 | 稳定销量事件键：`order-paid-sales:{orderId}` |
| order_id | BIGINT UNSIGNED | NOT NULL | uk_statistics_sales_order | 来源订单主键，仅用于幂等去重 |
| window_id | BIGINT UNSIGNED | NOT NULL | 支付时间索引参与 | 下单时所属窗口主键快照 |
| paid_at | DATETIME | NOT NULL | 支付时间索引参与 | 订单首次支付成功时间，按 UTC+8 归属月份 |
| event_version | SMALLINT UNSIGNED | NOT NULL | — | 事件格式版本，供历史兼容与排查 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 统计服务首次持久化事件时间 |

- 唯一键：`uk_statistics_sales_order(order_id)`；普通索引：`idx_statistics_sales_paid_at(paid_at, window_id)`；被明细表外键引用。

### statistics_sales_event_items（订单菜品明细事实）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | VARCHAR(80) | NOT NULL | 联合主键；外键 → events | 所属已支付事件键 |
| item_id | BIGINT UNSIGNED | NOT NULL | 联合主键；商品索引参与 | 订单明细主键快照，识别重复明细及选择最新菜名 |
| product_id | BIGINT UNSIGNED | NOT NULL | 商品索引参与 | 下单时菜品主键快照 |
| product_name | VARCHAR(255) | NOT NULL | — | 下单时菜品名称快照 |
| quantity | INT UNSIGNED | NOT NULL | — | 原始购买份数，退款不会减少销量 |

- 联合主键 `(event_id, item_id)`；索引 `idx_statistics_sales_item_product(product_id, item_id)`；外键 `fk_statistics_sales_item_event`。

### statistics_sales_window_totals（窗口销量汇总）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| period_type | VARCHAR(8) | NOT NULL | 联合主键；榜单排序索引参与 | 汇总周期：ALL 累计、MONTH 自然月 |
| period_key | CHAR(7) | NOT NULL | 联合主键；榜单排序索引参与 | ALL 固定 ALLTIME，自然月 YYYY-MM |
| window_id | BIGINT UNSIGNED | NOT NULL | 联合主键 | 窗口主键 |
| paid_quantity | BIGINT UNSIGNED | NOT NULL，默认 0 | 榜单排序索引参与 | 已支付订单原始购买总份数，不扣退款 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 汇总最后变更时间 |

- 联合主键 `(period_type, period_key, window_id)`；索引 `idx_statistics_window_quantity(period_type, period_key, paid_quantity)`。

### statistics_sales_product_totals（菜品销量汇总）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| period_type | VARCHAR(8) | NOT NULL | 联合主键；榜单排序索引参与 | 汇总周期：ALL 累计、MONTH 自然月 |
| period_key | CHAR(7) | NOT NULL | 联合主键；榜单排序索引参与 | ALL 固定 ALLTIME，自然月 YYYY-MM |
| window_id | BIGINT UNSIGNED | NOT NULL | 联合主键；榜单排序索引参与 | 下单时所属窗口主键 |
| product_id | BIGINT UNSIGNED | NOT NULL | 联合主键；榜单排序索引参与 | 下单时菜品主键 |
| product_name | VARCHAR(255) | NOT NULL | — | 最近一次订单中的菜品名称快照 |
| latest_item_id | BIGINT UNSIGNED | NOT NULL | — | 最近一次名称快照对应的订单明细主键，保证菜名只被更晚明细更新 |
| paid_quantity | BIGINT UNSIGNED | NOT NULL，默认 0 | 榜单排序索引参与 | 已支付订单原始购买总份数，不扣退款 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 汇总最后变更时间 |

- 联合主键 `(period_type, period_key, window_id, product_id)`；索引 `idx_statistics_product_rank(period_type, period_key, paid_quantity, product_id, window_id)`。

### statistics_sales_projection_meta（投影版本元数据）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| projection_id | TINYINT UNSIGNED | NOT NULL | 主键 | 固定单行，当前值 1 |
| version | BIGINT UNSIGNED | NOT NULL，默认 0 | — | MySQL 销量投影每次成功新增订单后递增的版本号，Redis 缓存对账依据 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 投影版本最后变更时间 |

**Redis 键（前缀 `statistics:ranking:{sales:v3}:`）**：`data`（ZSet，成员 `productId:windowId`）、`meta`（Hash，成员含菜名 JSON）、`seen`（订单去重 Set）、`ready`（缓存版本）、`generation`（fencing token）、`lock`（Redisson 键）。三个 Lua 脚本：`apply-sales-v3`（generation 校验、版本必须连续 +1、`seen` 幂等、数量上限 2^53-1、按明细 ID 前缀防菜名回退）、`rebuild-sales-v3`（全量重建）、`read-sales-v3`（版本匹配才返回）。

## 核心业务流程

### 事件消费与 MySQL 投影

1. 消费 `order-paid-sales-v2`（现行）与 `order-paid-sales-v1`（历史存量）；V1 事件需 Feign 回读订单服务支付销量快照，回读失败抛异常交 MQ 重投。
2. `SalesProjectionService.record` 单事务完成：事件首次插入 → 批量插入明细 → 按 ALL + MONTH 双周期累加窗口/菜品汇总 → 投影版本 +1。
3. 唯一键冲突时逐字段比对重复载荷：完全一致视为幂等成功，不一致抛异常交 MQ（防脏事件覆盖）。

### Redis 榜单维护

1. 事务提交后抢 Redisson 锁（wait 2s / lease 30s）。
2. 缓存版本 == MySQL 版本-1 且事件为最新时执行增量 Lua；否则锁内全量重建（`DEL` 后从 MySQL 重灌）。
3. fencing generation 自增，Lua 内拒绝旧 generation 写入，防锁过期后旧线程覆盖新缓存。

### 对账（`reconcile`）

默认每 300 秒：抢到锁即按累计汇总与全部事件全量重建缓存，修复死信、Redis 数据丢失等一切漂移。

### 读取

- 榜单：Lua 按版本读取；版本不匹配触发锁内重建后再读；重建失败抛「销量榜暂时无法获取，请稍后下拉刷新」，不做降级假数据。
- 月售：直接读 MONTH 汇总行，数据库即权威，无缓存兜底。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 事件重复消费 | 三层幂等：事件事实表双唯一键（event_id 主键 + order_id 唯一键）+ Lua `seen` Set + 投影版本比对；重复载荷逐字段一致性校验 | 完全一致的重放直接吸收；不一致的脏事件抛异常交 Broker 重投，不覆盖已投影数据 |
| 缓存并发更新（多消费者抢写） | Redisson 锁（wait 2s / lease 30s）内才允许增量或重建；增量 Lua 要求缓存版本严格等于 MySQL 版本-1 且连续 +1 | 版本不满足即放弃增量转全量重建；抢不到锁的对账周期下轮再试 |
| 锁过期后的旧线程写回 | fencing generation：拿锁时自增 generation，Lua 内拒绝旧 generation 写入 | 锁过期后旧线程的写入被 Lua 拒绝，新缓存不被覆盖 |
| 增量与重建竞态 | 重建 `DEL` 后从 MySQL 重灌，在锁内原子完成；读取 Lua 版本不匹配触发锁内重建后再读 | 重建失败返回「销量榜暂时无法获取」，不返回旧版本、不伪造数据 |
| 菜名回退 | Lua 按明细 ID 前缀比较：菜名快照只被更晚的明细更新 | 乱序事件不会把菜名改回旧值 |
| 对账任务与事件处理竞争 | 对账抢到 Redisson 锁才执行全量重建；锁内重建覆盖一切漂移（死信、Redis 数据丢失） | 未抢到锁本轮跳过，下轮再试 |
| 数量溢出 | Lua 先校验上限（2^53-1）再写入 | 达上限的写入被拒绝，避免半成功状态 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/statistics/rankings/sales?limit=` | JWT + CUSTOMER | limit 1-100 默认 50；返回名次、商品、窗口、销量与更新时间 |
| GET | `/statistics/windows/monthly-sales` | JWT + CUSTOMER | 当前自然月全窗口总份数 |
| GET | `/statistics/windows/{windowId}/monthly-sales` | JWT + CUSTOMER | 单窗口月总售 + 菜品月售 |
| GET | `/internal/statistics/rankings/sales` | X-Internal-Token | 供推荐服务，网关不路由 |

## 消息、异步与缓存设计

| binding | topic | 消费组 | 生产方 |
|---|---|---|---|
| salesStatisticsPaid-in-0 | `order-paid-sales-v2` | `statistics-sales-v2` | 订单服务 Outbox（同步发送） |
| legacySalesStatisticsPaid-in-0 | `order-paid-sales-v1` | `statistics-sales-v1` | 历史 topic，仅消费存量 |

- 幂等链：eventId + 数据库双唯一键 + Lua `seen` Set + 投影版本比对。
- 错误处理：任何 MySQL/Redis 故障抛异常不确认消息，RocketMQ 重投 16 次后进死信；对账任务兜底。
- 本服务不生产消息。

## 安全设计

无状态 JWT + RSA 验签 + jti 黑名单；学生角色限定查询接口；内部接口共享令牌；`/internal/**` 无网关路由。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `statistics.sales.reconcile-delay-ms` | 300000 | 榜单对账间隔 |
| `statistics.messaging.enabled` | true | 消息消费开关 |
| `internal.scope.token` | — | 服务间共享令牌 |
| `ROCKETMQ_NAMESERVER` | 127.0.0.1:9876 | MQ 地址 |

## 异常与失败行为

- 缓存版本落后超过 1 或抢锁失败：直接全量重建或返回服务不可用，绝不返回旧版本榜单。
- 重建一次把全部事件 ID 传入 Lua，事件规模极大后需改影子 Key 分批（代码注释已标注）。
- 数量上限 2^53-1（`MAX_EXACT_REDIS_INTEGER`），Lua 先校验上限再写入，避免半成功。

## 关键代码入口

`SalesRankingCacheService`（缓存版本对账、Lua 与 fencing 设计）→ `SalesProjectionService`（MySQL 投影与幂等）→ `apply-sales-v3.lua` / `rebuild-sales-v3.lua`（原子性细节）。

## 口径与执行约定

- 支付时间为空或历史记录无法关联支付单时不得伪造时间（不允许用订单更新时间或当前时间猜测，避免落入错误月份），该事件按失败处理交重试。
- 数据保留边界：事实表只存订单 ID、窗口 ID、支付时间与菜品快照，不复制用户身份、支付凭证或退款敏感信息。
- 并发口径：订单支付与取消并发时仅状态条件更新胜者写支付时间与事件，重复通知不覆盖时间、不重复写事件。
- 数据库账号最小授权：仅授予本库读写权限。
- 迁移执行：迁移前备份订单库与支付库并校验可读；脚本固定 utf8mb4 客户端字符集后幂等重跑（首次执行曾因字符集导致中文状态条件未命中）。
- 学生端月售刷新规则：从其他页面返回首页时只静默重读月售汇总（不重载窗口目录、不重置筛选、不重播动画）；已有成功数据时请求失败保留旧值，首次失败显示占位符，不把未知数据显示成零。

## 已知限制与未验证项

- 销量口径固定为「已支付份数，退款不冲减」，与商品销量榜展示一致；不提供退款后销量修正。
- 榜单缓存重建的规模化方案（影子 Key 分批）尚未实施。
- 运行联调与对账任务的实际修复效果未验证。

## 关联文档

- `docs/database/migrations/statistics_db/`：建表与历史数据迁移脚本。
- `recommendation-service/README.md`：热榜消费方。
- 根 README「跨模块业务链路」：支付 → 销量 → 榜单链路。
