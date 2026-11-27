# stock-service 库存服务

> 库存域服务：下单扣减、取消回补、部分退款回补、库存查询、变更事件发布与快照失效。默认端口 `9104`，数据库 `stock_db`。只暴露 `/internal/**` 服务间接口，无顾客端入口；数据库是唯一事实来源，Redis 快照仅是可再生的加速层。扣减作为订单下单 Seata 全局事务的本地分支执行。

## 模块定位与职责边界

- 拥有库存事实：可用量、扣减/回补/调整流水、版本号。
- 不含顾客端接口与网关路由；所有调用方为订单（扣减/回补/退款回补）、商品（库存聚合）、推荐（候选复核）。
- Redis 快照丢失或漂移均可由数据库重建，事件只做缓存失效通知，不承载库存事实。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server / Validation / Actuator |
| 微服务 | Nacos Discovery、Spring Cloud Stream RocketMQ、**Seata**（RM 角色，代理数据源写 undo_log，tx-service-group `order-system-tx-group`，TC `127.0.0.1:9109`） |
| 数据 | MyBatis-Plus 3.5.8 + Druid + MySQL `stock_db`（默认 `127.0.0.1:9095`，账号 `stock_service`） |
| 缓存 | Redis：可用量快照（前缀 `stock:available:`，TTL 300 秒）+ 内嵌 Lua 预扣 |
| 共享模块 | common-core、common-web、common-redis、stock-api |

- `spring.profiles.default: stock-mq`；出绑定 `stockChanged-out-0` 与入绑定 `stockChanged-in-0` 共用 topic `stock-changed-v1`（生产组 `stock-change-outbox-v1`，消费组 `stock-cache-invalidator-v1`）；消息键 = eventId 保证按库存行有序。
- 定时对账默认开启（延迟 60 秒、间隔 600 秒）；共享令牌 `internal.scope.token`。
- `stock-api`：`StockServiceClient`、`StockRefundClient`、降级工厂与 DTO；错误码枚举区分「库存不足」与「服务不可用」，供订单服务识别。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `StockInternalController` | 扣减、回补、库存查询、推荐批量查询 |
| `StockRefundInternalController` | 部分退款回补 |
| `StockServiceImpl` | 扣减/回补的「三防线」实现 |
| `StockRefundServiceImpl` | 退款回补（幂等 + 余额校验 + 提交后快照对齐） |
| `StockMapper` | FOR UPDATE 行锁、乐观锁条件更新、对账全表读 |
| `StockFlowMapper` | 流水幂等计数、可回补余额、退款流水查询 |
| `StockRedisScript` | 两条内嵌 Lua（原子预扣 + 补偿）与快照读写 |
| `StockReconcileTask` | 定时对账（快照修复 + 流水自洽校验） |
| `StockEventOutboxWriter` / `StockEventPublisher` | 同事务写事件（携带 Seata XID）+ 门控发布 |
| `StockEventConsumer` | 自消费失效本地快照（幂等表判重） |
| `StockSecurityConfig` | 内部接口令牌校验 + JWT 黑名单 fail-closed |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/stock_db/`（V1–V3）。

### stock（库存主表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| stock_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 库存记录主键 |
| window_id | BIGINT UNSIGNED | NOT NULL | uk_window_product | 所属档口 ID |
| product_id | BIGINT UNSIGNED | NOT NULL | uk_window_product；idx_product | 菜品 ID |
| total_qty | INT | NOT NULL，默认 0 | — | 总可售上限 |
| available_qty | INT | NOT NULL，默认 0 | — | 当前可售数量，数据库为准 |
| version | INT | NOT NULL，默认 0 | — | 乐观锁版本号 |
| is_deleted | TINYINT | NOT NULL，默认 0 | — | 逻辑删除标记 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 唯一键：`uk_window_product(window_id, product_id)`；索引：`idx_product(product_id)`。

### stock_flow（库存流水）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| flow_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 流水主键 |
| biz_type | VARCHAR(32) | NOT NULL | uk_biz_flow；退款溯源索引参与 | ORDER_DEDUCT 下单扣减 / ORDER_RESTORE 回补 / ADMIN_ADJUST 调整 |
| biz_ref | VARCHAR(64) | NOT NULL | uk_biz_flow | 业务单据号（订单号），幂等依据 |
| stock_id | BIGINT UNSIGNED | NOT NULL | uk_biz_flow；普通索引；退款溯源索引首列 | 库存记录 ID |
| window_id | BIGINT UNSIGNED | NOT NULL | — | 档口 ID（冗余，便于按档口对账） |
| product_id | BIGINT UNSIGNED | NOT NULL | — | 菜品 ID（冗余） |
| change_qty | INT | NOT NULL | — | 变动数量：扣减为负、回补为正 |
| before_qty | INT | NOT NULL | — | 变动前可售数量 |
| after_qty | INT | NOT NULL | — | 变动后可售数量 |
| operator_id | BIGINT UNSIGNED | 可空 | — | 操作者 user_id，系统触发为空 |
| remark | VARCHAR(255) | 可空 | — | 备注 |
| original_biz_ref | VARCHAR(64) | 可空（V3 新增） | 退款溯源索引参与 | 部分退款关联的原订单号，供一笔订单多次按菜品回补 |
| created_at | DATETIME | NOT NULL，默认当前时间 | idx_created | 创建时间 |

- 唯一键：`uk_biz_flow(biz_type, biz_ref, stock_id)`；索引：`idx_stock(stock_id)`、`idx_created(created_at)`、`idx_stock_refund_origin(stock_id, original_biz_ref, biz_type)`。

### stock_event_outbox（库存变更事件）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | VARCHAR(192) | NOT NULL | 主键 | 稳定事件键：`stock-flow:{bizType}:{bizRef}:{stockId}` |
| biz_type | VARCHAR(32) | NOT NULL | uk_stock_event_flow | 变动类型 |
| biz_ref | VARCHAR(64) | NOT NULL | uk_stock_event_flow | 业务单据号 |
| stock_id | BIGINT UNSIGNED | NOT NULL | uk_stock_event_flow | 库存记录 ID |
| window_id | BIGINT UNSIGNED | NOT NULL | — | 档口 ID |
| product_id | BIGINT UNSIGNED | NOT NULL | — | 菜品 ID |
| available_qty | INT | NOT NULL | — | 该流水后的数据库可售数量，仅作通知快照 |
| stock_version | INT | NOT NULL | — | 该流水后的库存版本 |
| xid | VARCHAR(128) | 可空 | XID 索引 | Seata 全局事务 ID；发布门控依据，空表示本地事务 |
| attempts | INT | NOT NULL，默认 0 | — | 发布失败次数 |
| next_attempt_at | DATETIME | NOT NULL，默认当前时间 | 待发布扫描索引参与 | 下次发布尝试时间 |
| published_at | DATETIME | 可空 | 待发布扫描索引参与 | Broker 确认接收时间 |
| last_error | VARCHAR(255) | 可空 | — | 最近一次发布错误类型 |
| created_at | DATETIME | NOT NULL，默认当前时间 | 待发布扫描索引参与 | 事件创建时间 |

- 唯一键：`uk_stock_event_flow(biz_type, biz_ref, stock_id)`；索引：`idx_stock_event_pending(published_at, next_attempt_at, created_at)`、`idx_stock_event_xid(xid)`。

### stock_event_consumed（消费幂等表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | VARCHAR(192) | NOT NULL | 主键 | 已处理的库存变更事件键 |
| processed_at | DATETIME | NOT NULL，默认当前时间 | — | 消费处理时间 |

### undo_log（Seata AT 回滚日志，官方标准结构）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| branch_id | BIGINT | NOT NULL | 主键 | 分支事务 ID |
| xid | VARCHAR(128) | NOT NULL | ux_undo_log | 全局事务 ID |
| context | VARCHAR(128) | NOT NULL | — | undo_log 序列化方式 |
| rollback_info | LONGBLOB | NOT NULL | — | 回滚镜像 |
| log_status | INT | NOT NULL | — | 0 正常、1 已防御 |
| log_created / log_modified | DATETIME(6) | NOT NULL | — | 创建/修改时间 |

- 唯一键：`ux_undo_log(xid, branch_id)`。

## 核心业务流程

### 下单扣减（`StockServiceImpl.deduct`，Seata 全局事务分支）

逐行执行「三防线」：
1. 行锁读取（无记录报未找到）→ 流水幂等预检（已扣直接返回）。
2. Redis Lua 原子预扣：快照缺失先用数据库值预热重试一次；Redis 异常降级纯数据库扣减（不阻断下单）；不足报库存不足。
3. 数据库条件更新：`version` 乐观锁 + `available_qty >= quantity`，失败报库存不足。
4. 写流水 + 同事务写事件 Outbox（携带全局事务 ID）。
整体失败时逐条 Lua 补偿 Redis 快照，随全局事务回滚数据库变更。

### 取消回补

行锁 → 幂等预检 → 校验存在对应扣减流水（无则告警跳过，避免凭空加库存）→ 可回补余额校验（覆盖整单取消 + 多次退款累计）→ 累加可用量 → 写流水与 Outbox → 对齐快照。

### 退款回补

参数校验（订单号、退款号格式）→ 行锁 → 按退款号幂等（命中则核对原订单与数量后返回）→ 可回补余额校验 → 累加 → 写退款流水（引用退款号 + 原订单号）→ Outbox → **事务提交后**再重置快照（回滚时不暴露未生效数量）。

### 对账（`StockReconcileTask`）

- 快照与数据库不一致：以数据库改写快照；Redis 异常则本轮跳过快照比对。
- 流水自洽校验：`总量 + Σ流水 == 可用量`，不一致只告警不改写（事实数据不由对账修改）。

### 事件发布与消费

- 发布（每秒）：**Seata 门控**——事件仍带未决全局事务 XID 时不发布；全局回滚时库存与 Outbox 一并撤销。发送成功标记，失败指数退避（≤300 秒）。
- 消费：幂等表判重 → 删除本地快照 → 标记已消费；删除失败不落标记，交 Broker 重投。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 同一订单重复扣减 | 流水唯一键 `(biz_type, biz_ref, stock_id)`：写流水前先幂等预检，唯一键冲突兜底；已扣过的引用直接返回原结果 | 幂等命中返回成功，不产生第二次扣减 |
| 超卖 | 数据库条件更新 `available_qty >= ? AND version = ?`：乐观锁版本 + 余量校验双条件，只有一条 UPDATE 的赢家生效 | 0 行更新报「库存不足」，随调用方 Seata 全局回滚 |
| 并发扣减的行竞争 | 扣减/回补均先 `SELECT ... FOR UPDATE` 锁库存行，串行化同一商品的所有变更 | 行锁排队等待，由 InnoDB 超时控制 |
| Redis 与数据库的原子性 | 单条 Lua 脚本完成「快照存在性 → 余量 → 预扣」三步，预扣失败不写数据库；数据库扣减失败时逐条执行补偿 Lua 恢复快照 | Redis 故障降级为纯数据库扣减（放弃预扣，不阻断下单）；补偿失败由 300 秒 TTL 与对账任务收敛 |
| 快照与数据库漂移 | 对账任务以数据库为准改写快照；流水自洽校验（总量+Σ流水=可用量）不一致只告警不改写 | Redis 异常本轮跳过快照比对，下轮继续 |
| 事件重复发布/乱序 | Outbox 唯一键 `(biz_type, biz_ref, stock_id)` 保证同流水一条事件；消息键取 eventId 按库存行有序；Seata 门控：XID 未决不发布 | 消费侧 `stock_event_consumed` 主键判重，重复事件直接标记已消费 |
| 全局回滚的中间状态 | 扣减为 Seata AT 分支，undo_log 保存回滚镜像；回补在全局事务外按业务引用独立幂等 | 分支回滚由 TC 协调，业务不感知 |

## 接口清单

全部内部接口（`X-Internal-Token`，业务失败以 HTTP 200 + STOCK_\* 业务码返回）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/stocks/deduct` | 下单扣减 |
| POST | `/internal/stocks/restore` | 取消/整单回补 |
| POST | `/internal/stocks/refund` | 部分退款回补（无降级工厂） |
| GET | `/internal/stocks/{windowId}/{productId}` | 单品库存（无记录返回空数据） |
| GET | `/internal/stocks/windows/{windowId}` | 窗口全部库存（商品服务聚合） |
| POST | `/internal/stocks/recommendations/batch` | 批量库存复核（1–50 组合；缺失行不返回，零库存如实返回） |

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 生产 | `stock-changed-v1`（Outbox + Seata 门控 + 按库存行有序） |
| 消费 | 同 topic 仅本服务自消费（快照失效） |
| 幂等 | 发布侧 Outbox 唯一键 + 消费侧幂等表 + 流水唯一键 |
| 快照 | TTL 300 秒自动回源收敛；Lua 预扣可降级 |

## 安全设计

内部接口共享令牌，其余路径需 JWT（黑名单不可确认拒绝）；不对外暴露任何顾客端接口。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `stock.snapshot.key-prefix / ttl-seconds` | `stock:available:` / 300 | 快照前缀与过期 |
| `stock.reconcile.enabled / initial-delay-ms / fixed-delay-ms` | true / 60000 / 600000 | 对账任务 |
| `stock.messaging.enabled / publish-delay-ms` | — / 1000 | 事件发布 |
| `SEATA_*` / `ROCKETMQ_NAMESERVER` | 本地 TC / 127.0.0.1:9876 | 事务与 MQ 地址 |

## 异常与失败行为

- Feign 降级工厂一律返回服务不可用，迫使调用方回滚全局事务，绝不伪报成功；退款客户端不配降级，失败显式留痕。
- Redis 完全不可用降级纯数据库扣减；对账只修派生数据、不修事实数据。
- JWT 黑名单无法确认按无效令牌拒绝（fail-closed）。

## 关键代码入口

`StockServiceImpl`（三防线与 Seata 分支）→ `StockRedisScript`（Lua 预扣与补偿）→ `StockEventPublisher`（XID 门控发布）→ `StockReconcileTask`（对账边界）→ `stock_db` 迁移脚本。

## 运行边界与配套约定

- 发布失败指数退避上限为 300 秒（`LEAST(300, 2^min(attempts,8))`，以代码为准）；消费失败显式重抛交 RocketMQ 重投 16 次后进入消费组死信队列。
- 未携带 Seata XID 的本地库存事务在本地提交后即可发布事件；带 XID 的事件受全局事务门控。
- 发布成功的 Outbox 记录与消费幂等记录当前不自动清理，保留期与归档策略待定。
- 库存初始化脚本规则：只为无库存记录的菜品新增（已有记录含逻辑删除保持原样）；同时设置 total_qty 与 available_qty 基线且不写初始变动流水；空值、负数、窗口不匹配被校验拒绝，并通过校验表防误写。
- 顾客侧聚合契约：商品服务 `GET /api/products/windows/{windowId}/stocks`（CUSTOMER）经内部接口批量读取，仅返回当前可售菜单菜品；库存服务不可用或数据无效时商品服务返回 503，绝不误报零库存；库存不写入菜单缓存。
- 错误码契约：库存不足 `COMMON_409`、服务不可用 `COMMON_503`，定义在 stock-api 供订单服务区分语义。
- Windows 本地开发时 Seata 客户端使用 `registry.type: file` + `grouplist.default` 直连 `127.0.0.1:9109`，避免注册中心返回 Docker 内网不可路由地址（订单服务同理）。

## 已知限制与未验证项

- 管理端人工调整库存（ADMIN_ADJUST）有流水类型但无管理接口。
- 快照 Lua 预扣为行级原子，跨行整单原子性由数据库事务与 Seata 保证。
- 大促场景 Redis 预扣的容量收益未做压测验证。

## 关联文档

- `order-service/README.md`：下单/取消/退款中的库存调用时序。
- `product-service/README.md`：库存聚合展示。
- 根 README「跨模块业务链路」：下单扣减 Seata 链路。
