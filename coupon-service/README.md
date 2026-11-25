# coupon-service 优惠券服务

> 负责平台满减券的模板管理、学生领取、订单锁券、支付核销与关单释放全生命周期。默认端口 `9113`，数据库 `coupon_db`，锁券/释放由订单服务在 Seata 全局事务内调用，核销由支付成功事件驱动。首期固定为平台发行、平台出资（`issuer_type=PLATFORM`、`funding_party=PLATFORM`）。

## 模块定位与职责边界

- 平台满减券唯一事实源：模板数量、领取状态、持券状态、使用流水都在本库内维护。
- 三类入口：学生端领取与查询、管理端模板生命周期、订单内部锁券与释放。
- `coupon-api` 为共享 Feign 契约模块（`CouponServiceClient`、`CouponServiceClientFallbackFactory`、`CouponLockRequest`/`CouponReleaseRequest`/`CouponPaidEvent`、`CouponLockVO`），由订单服务与本服务共同依赖。

**边界：** 不读写用户、商品、订单库；跨库 ID 仅作业务关联、不建外键。管理操作的授权与窗口校验通过 Feign 实时复核，任一依赖失败即拒绝（fail-closed）。不支持折扣券、无门槛券、券码、自动派券与商家出资券。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）、Validation、Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Seata（tx-service-group `order-system-tx-group`，TC 127.0.0.1:9109） |
| 消息 | Spring Cloud Stream RocketMQ（函数式绑定，仅消费） |
| 数据 | MyBatis-Plus 3.5.8（注解 SQL）、Druid、MySQL `coupon_db`（默认 `127.0.0.1:9095`，账号 `coupon_service`） |
| 缓存 | Redis（仅 JWT 注销黑名单，不作业务事实） |

- 端口 `9113`，服务名 `coupon-service`；`spring.profiles.include: coupon-mq` 使任何 profile 都加载 MQ 配置。
- Druid `max-active=10`，连接初始化 `SET SESSION time_zone='+08:00'`；Actuator 仅暴露 health,info。
- JWT：issuer `http://localhost:9094`、RSA 公钥验签、黑名单前缀 `auth:access-token:blacklist:`。
- 服务间共享令牌 `internal.scope.token`（与订单、用户、商品服务一致）。
- MQ 绑定：`couponPaid-in-0` → topic `order-coupon-paid-v1`，消费组 `coupon-service-coupon-paid-v1`；`max-attempts: 1`（事务性消费自行抛异常交 RocketMQ 重投）、`CONSUME_FROM_FIRST_OFFSET`、`max-reconsume-times: 16`、非顺序消费。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `CouponServiceApplication` | 启动入口；启用 `UserScopeInternalClient`、`WindowInternalClient`、`OrderCouponConsumeAckClient` 三个 Feign 客户端 |
| `CouponController` | 学生端 `/coupons`：可领列表、领券、我的券；用户 ID 一律取自 JWT subject |
| `CouponManagementController` | 管理端 `/coupons/admin/templates`：列表、创建、发布、停领、停用 |
| `CouponInternalController` | 内部 `/internal/coupons`：锁券、释放；先验 `X-Internal-Token`，业务失败以 HTTP 200 + 错误业务码返回，避免 Feign 降级吞掉原因 |
| `CouponServiceImpl` | 全部业务逻辑：领取、模板生命周期、锁券/释放/核销 |
| `CouponAuthorizationServiceImpl` | 向用户服务复核操作者平台数据范围、向商品服务复核窗口存在 |
| `CouponTemplateMapper` / `CouponTemplateWindowMapper` | 模板查询与 `reserveClaim` 原子预占、模板-窗口关系维护 |
| `UserCouponMapper` / `CouponUseRecordMapper` | 持券查询与状态条件更新、订单级使用流水 |
| `CouponAdminActionMapper` | 管理操作审计 |
| `CouponPaidMessaging` | RocketMQ 消费者，支付成功后核销 |
| `CouponSecurityConfig` 及安全错误处理器 | 无状态 JWT 过滤链、roles→ROLE_ 转换、黑名单校验、统一 401/403 JSON |

无定时任务：过期通过「我的券」查询时惰性迁移，不依赖后台扫描。

## 领域模型与数据表

迁移脚本：`docs/database/migrations/coupon_db/V1__create_coupon_tables.sql`（含 `undo_log`）。

### coupon_template（优惠券发行模板）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| coupon_template_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 模板主键 |
| title | VARCHAR(120) | NOT NULL | — | 学生端展示标题 |
| min_spend | DECIMAL(12,2) | NOT NULL | CHECK >0 | 满足使用资格的适用商品金额门槛 |
| discount_amount | DECIMAL(12,2) | NOT NULL | CHECK >0 且 < min_spend | 固定优惠金额 |
| total_quantity | BIGINT UNSIGNED | NOT NULL | CHECK >0 | 本模板最多可领取数量 |
| claimed_quantity | BIGINT UNSIGNED | NOT NULL，默认 0 | CHECK ≤ total_quantity | 已成功领取数量，原子条件更新维护 |
| claim_start_at | DATETIME | NOT NULL | CHECK < claim_end_at | 开始领取时间 |
| claim_end_at | DATETIME | NOT NULL | — | 停止领取时间 |
| valid_from | DATETIME | NOT NULL | CHECK < valid_until | 券可使用开始时间 |
| valid_until | DATETIME | NOT NULL | — | 券可使用截止时间 |
| scope_type | VARCHAR(16) | NOT NULL | — | 适用范围：CAMPUS/WINDOWS |
| issuer_type | VARCHAR(16) | NOT NULL，默认 'PLATFORM' | — | 发行主体，首期仅平台 |
| funding_party | VARCHAR(16) | NOT NULL，默认 'PLATFORM' | — | 优惠承担方，首期仅平台 |
| status | VARCHAR(16) | NOT NULL，默认 'DRAFT' | 可领列表索引首列 | DRAFT/PUBLISHED/CLAIM_STOPPED/STOPPED |
| created_by | BIGINT UNSIGNED | NOT NULL | — | 创建人用户主键 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 索引：`idx_coupon_claimable(status, claim_start_at, claim_end_at)`。

### coupon_template_window（模板适用窗口）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| coupon_template_id | BIGINT UNSIGNED | NOT NULL | 联合主键 | 优惠券模板主键 |
| window_id | BIGINT UNSIGNED | NOT NULL | 索引 idx_coupon_window 首列 | 适用窗口 ID，不建跨服务外键 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 关系创建时间 |

### user_coupon（学生持券记录）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| user_coupon_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 用户券主键 |
| coupon_template_id | BIGINT UNSIGNED | NOT NULL | uk_user_coupon_template | 来源模板 |
| user_id | BIGINT UNSIGNED | NOT NULL | uk_user_coupon_template；状态索引首列 | 领取用户主键 |
| status | VARCHAR(16) | NOT NULL，默认 'AVAILABLE' | 状态索引 | AVAILABLE/LOCKED/USED/EXPIRED/REVOKED |
| valid_from | DATETIME | NOT NULL | — | 领取时固化的可用开始时间 |
| valid_until | DATETIME | NOT NULL | 状态索引末列 | 领取时固化的可用截止时间 |
| locked_order_no | VARCHAR(64) | 可空 | uk_user_coupon_locked_order | 当前绑定订单号，锁定期间唯一 |
| claimed_at | DATETIME | NOT NULL，默认当前时间 | — | 领取时间 |
| used_at | DATETIME | 可空 | — | 确认支付后核销时间 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 唯一键：`uk_user_coupon_template(coupon_template_id, user_id)`（每模板每人限领一张）、`uk_user_coupon_locked_order(locked_order_no)`；索引：`idx_user_coupon_status(user_id, status, valid_until)`。

### coupon_use_record（锁券/核销/释放流水）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| coupon_use_record_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 流水主键 |
| user_coupon_id | BIGINT UNSIGNED | NOT NULL | uk_coupon_use_user_order | 用户券主键 |
| coupon_template_id | BIGINT UNSIGNED | NOT NULL | 模板索引首列 | 模板主键 |
| user_id | BIGINT UNSIGNED | NOT NULL | — | 使用用户主键 |
| order_no | VARCHAR(64) | NOT NULL | uk_coupon_use_order | 关联订单号，订单级幂等键 |
| status | VARCHAR(16) | NOT NULL | 模板索引 | LOCKED/USED/RELEASED |
| discount_amount | DECIMAL(12,2) | NOT NULL | — | 锁定时优惠金额快照 |
| platform_funded_amount | DECIMAL(12,2) | NOT NULL | — | 平台承担的优惠成本快照 |
| window_funded_amount | DECIMAL(12,2) | NOT NULL，默认 0 | — | 窗口承担金额，首期固定 0 |
| locked_at | DATETIME | NOT NULL，默认当前时间 | — | 首次锁定时间 |
| used_at | DATETIME | 可空 | 模板索引末列 | 核销时间 |
| released_at | DATETIME | 可空 | — | 释放时间 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 唯一键：`uk_coupon_use_user_order(user_coupon_id, order_no)`、`uk_coupon_use_order(order_no)`；索引：`idx_coupon_use_template(coupon_template_id, status, used_at)`。

### coupon_admin_action_record（管理操作审计）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| action_id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 操作流水主键 |
| coupon_template_id | BIGINT UNSIGNED | NOT NULL | 模板索引首列 | 被操作模板 ID |
| operator_id | BIGINT UNSIGNED | NOT NULL | — | 执行操作的总经理用户 ID |
| action_type | VARCHAR(24) | NOT NULL | — | CREATE/PUBLISH/STOP_CLAIMS/STOP_USE |
| previous_status | VARCHAR(16) | 可空 | — | 操作前状态，创建时为空 |
| new_status | VARCHAR(16) | NOT NULL | — | 操作后状态 |
| created_at | DATETIME | NOT NULL，默认当前时间 | 模板索引末列 | 操作时间 |

- 索引：`idx_coupon_admin_action_template(coupon_template_id, created_at)`。

### undo_log（Seata AT 回滚日志）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| branch_id | BIGINT | NOT NULL | 主键（非自增） | Seata 分支事务 ID |
| xid | VARCHAR(128) | NOT NULL | ux_undo_log | Seata 全局事务 ID |
| context | VARCHAR(128) | NOT NULL | — | undo_log 序列化方式 |
| rollback_info | LONGBLOB | NOT NULL | — | 回滚镜像数据 |
| log_status | INT | NOT NULL | — | 0 正常、1 已防御 |
| log_created / log_modified | DATETIME(6) | NOT NULL | — | 创建/修改时间 |

- 唯一键：`ux_undo_log(xid, branch_id)`。

## 核心业务流程

### 模板生命周期（管理端）

1. 创建（`CouponServiceImpl.createTemplate`）：先向用户服务复核操作者平台范围 → 校验金额两位小数且优惠额<门槛、标题与数量、时间顺序 → 窗口 ID 去重（≤100）并向商品服务确认存在 → 插入模板（DRAFT，已领量为 0，发行/出资方服务端固定 PLATFORM）→ 写窗口关系与审计。
2. 发布/停领/停用：`FOR UPDATE` 取模板行锁后按原状态条件更新，失败抛「状态已变化」；每次变更写审计。
3. 停用（`stopUse`）：先改模板为 STOPPED，再撤销所有 AVAILABLE 持券（REVOKED），不动已锁定的券；与锁券保持「模板先锁」的加锁顺序防死锁。

### 领取

1. 校验模板 PUBLISHED 且处于领取窗口。
2. `reserveClaim` 条件更新：`claimed_quantity+1 WHERE status='PUBLISHED' AND 时间窗 AND claimed_quantity<total_quantity`，单条 SQL 原子防超发（不用缓存作数量事实）。
3. 插入持券记录（AVAILABLE + 有效期快照）；唯一键冲突转「已领取过」，插入失败回滚预占数量。

### 锁券（订单服务在 Seata 全局事务内调用）

1. 参数校验（`eligibleAmount` 正数两位小数）。
2. 固定加锁顺序：先模板行锁，再持券行锁。
3. 幂等：同券同订单已有 LOCKED 流水直接返回原优惠快照；同订单二次锁券拒绝。
4. 校验持券人、AVAILABLE、模板状态、有效期、门槛 `eligibleAmount >= minSpend`、WINDOWS 范围匹配。
5. 条件更新 AVAILABLE→LOCKED 并写 `locked_order_no`，失败抛并发冲突。
6. 插入 LOCKED 使用流水（金额快照），返回优惠信息。

### 释放与核销

- 释放（订单确认关闭后调用）：无绑定券直接返回；仅 LOCKED 可释放，按时效与模板状态转入 EXPIRED/REVOKED/AVAILABLE，清空订单号并释放流水。
- 核销（消息驱动）：消费 `order-coupon-paid-v1`，校验事件与 `locked_order_no` 匹配后条件更新 LOCKED→USED 并核销流水；已 USED 幂等返回。**只有支付成功事件能把 LOCKED 推进到 USED**。
- 核销完成后回调订单服务 `/internal/orders/coupon-consume/confirmed` 确认订单侧 Outbox；未确认则抛异常不确认消息，交 RocketMQ 重投。

### 状态机

- 模板：DRAFT → PUBLISHED → CLAIM_STOPPED → STOPPED（DRAFT 也可直接 STOPPED）。
- 持券：AVAILABLE → LOCKED → USED；LOCKED 释放后按情况回到 AVAILABLE/EXPIRED/REVOKED；AVAILABLE 可被停用撤销（REVOKED）或惰性过期（EXPIRED）。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 同订单重复锁券 | 使用流水唯一键 `uk_coupon_use_order(order_no)`：锁券前查同订单已有 LOCKED 流水——同券命中直接返回原优惠快照（幂等），换券拒绝；释放后允许新流水（唯一键随状态流转配合条件更新） | 幂等命中返回原快照；换券/重复加券返回明确业务错误 |
| 领取超发 | 领取用单条条件更新 `reserveClaim`：`claimed_quantity+1 WHERE status='PUBLISHED' AND 时间窗 AND claimed_quantity<total_quantity`，不依赖缓存数量 | 0 行更新返回「已领完」，绝不超发 |
| 每人重复领取 | 持券唯一键 `(coupon_template_id, user_id)`：先查后插，插入唯一键冲突转「已领取过」并回查返回原记录 | 预占数量回滚（同一事务），不产生脏预占 |
| 领取成功但持券写入失败 | 预占与插券同一本地事务，整体回滚 | 数量回到原值，无半成功状态 |
| 锁券/释放与停用并发 | 固定加锁顺序：先模板行 `FOR UPDATE`，再持券行 `FOR UPDATE`，两处操作同序防死锁；释放按模板状态与时效决定去向（AVAILABLE/EXPIRED/REVOKED） | 状态已变返回「优惠券状态已变化」；停用撤销只动 AVAILABLE，不碰 LOCKED |
| 管理端并发变更模板 | 全部状态变更走 `FOR UPDATE` 取行锁 + 原状态条件更新，前后状态写审计 | 冲突返回「状态已变化，请刷新后重试」 |
| 核销消息重投 | 支付成功事件按事件与 `locked_order_no` 匹配校验；核销条件更新 `LOCKED→USED` 唯一赢家；已 USED 幂等返回成功 | 重复消息被吸收；事件不匹配抛异常交 Broker 重投 16 次后进死信 |
| 核销与订单 Outbox 回执 | 核销成功后回调订单服务确认；确认失败则不确认消息（抛异常），RocketMQ 重投直至回执成功 | 回执缺失由订单侧 Outbox 超时补偿重发（指数退避，最大 300 秒） |

## 接口清单

学生端（`/coupons`，仅 `CUSTOMER`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/coupons/claimable` | 可领模板列表（含剩余量，≤100 条） |
| POST | `/coupons/{templateId}/claim` | 领取，返回持券记录 |
| GET | `/coupons/mine?status=` | 我的券（≤200 条；查询时惰性过期） |

管理端（`/coupons/admin/templates`，仅 `GENERAL_MANAGER`，服务层再复核平台数据范围）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/coupons/admin/templates` | 模板列表（≤200 条） |
| POST | `/coupons/admin/templates` | 创建模板（标题/门槛/优惠额/总量/窗口/时间） |
| POST | `/coupons/admin/templates/{id}/publish` | 发布 |
| POST | `/coupons/admin/templates/{id}/stop-claims` | 停止领取 |
| POST | `/coupons/admin/templates/{id}/stop-use` | 停用并撤销可用券 |

内部（`/internal/coupons`，`X-Internal-Token`，网关不路由）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/coupons/lock` | 锁券，失败以 HTTP 200 + 业务码返回 |
| POST | `/internal/coupons/release` | 按订单号释放 |

匿名放行：`/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**`、`/internal/**`（内部由令牌校验）。

## 消息、异步与缓存设计

- 仅消费一个事件：支付成功核销（见上文）。本服务不生产消息；生产方是订单服务的 `order_coupon_consume_outbox`（topic `order-coupon-paid-v1`，同步发送）。
- 幂等：按订单号唯一键 + 持券状态条件更新；重复核销返回成功。
- 死信：依赖订单侧 Outbox 超时补偿——消费回执确认超时后指数退避重发（最大间隔 300 秒）。
- 不使用缓存承载业务事实；Redis 仅承担 JWT 注销黑名单。

## 安全设计

- 无状态 JWT 资源服务器：RSA 公钥验签 + issuer 校验，要求令牌携带 jti 并查 Redis 黑名单；黑名单不可查时一律拒绝（fail-closed）。
- 内部接口由 `X-Internal-Token` 共享令牌保护，方法内先行校验。

## 配置项清单

| 配置 | 说明 |
|---|---|
| `COUPON_DB_URL/USERNAME/PASSWORD` | 数据库连接，默认 `127.0.0.1:9095/coupon_db` |
| `REDIS_HOST/PORT/PASSWORD` | 黑名单 Redis |
| `NACOS_SERVER_ADDR/USERNAME/PASSWORD` | 注册中心 |
| `JWT_ISSUER/JWT_PUBLIC_KEY_LOCATION/JWT_BLACKLIST_KEY_PREFIX` | 令牌验签与黑名单 |
| `INTERNAL_SCOPE_TOKEN` | 服务间共享令牌 |
| `SEATA_TX_SERVICE_GROUP/SEATA_TC_GROUPLIST` | Seata 事务分组与 TC 地址 |
| `ROCKETMQ_NAMESERVER` | MQ NameServer |
| `SWAGGER_ENABLED/SWAGGER_GATEWAY_BASE_URL` | 接口文档开关 |

## 异常与失败行为

- 订单侧 Feign 降级（`CouponServiceClientFallbackFactory`）fail-closed：锁券/释放失败一律返回「优惠券服务暂时不可用」，绝不伪装成功，由调用方 Seata 事务回滚。
- 授权复核（用户范围、窗口存在）失败统一「暂时无法确认，请稍后重试」；范围不符直接拒绝。
- 并发失败统一「优惠券状态已变化，请重新选择/刷新后重试」；JWT 缺 jti 或黑名单命中返回 invalid_token。

## 关键代码入口

读代码建议按此顺序：`CouponServiceImpl`（业务全集）→ `CouponInternalController`（锁券/释放入口）→ `CouponPaidMessaging`（核销链路）→ `coupon_db` V1 迁移脚本（表结构）。

## 核销回执与金额约定

- 订单侧核销 Outbox 三态语义：`PENDING` 待发送 → `PUBLISHED` 已进入 Broker 等待回执 → `CONFIRMED` 券服务提交核销后的最终确认。
- 优惠金额分摊：按各行原价比例分摊、精确到分，舍入尾差按确定顺序由订单行承接，全单金额守恒校验。
- 释放与核销的订单状态前提：不能仅因锁定时间过期就释放（订单可能已扣款但事件未到）；订单取消必须先完成支付关单并回补库存后才调释放。
- 退款不退券：支付成功后发生全额退款，首期不返还已核销的优惠券。
- 最低实付：使用优惠券后的应付金额必须大于零，保留最低实付避免零元支付流程。
- 历史订单（券功能上线前）回填口径：订单优惠额为 0、应付等于总额，明细级优惠与实付等于行金额。
- 设计稿曾规划的锁券异常对账定时任务未实现；当前以释放接口与订单 Outbox 回执机制覆盖。

## 已知限制与未验证项

- 首期仅支持平台满减券；折扣券、无门槛券、券码、自动派券、商家出资均未实现。
- 过期靠查询惰性迁移，列表外不存在主动过期动作。
- 源码与静态检查已完成；`coupon_db` V1 已执行，订单侧金额快照迁移与全链路运行联调尚未完成。

## 关联文档

- `docs/COUPON_SERVICE.md`：设计范围、状态机与 Outbox 补偿细节。
- `docs/database/migrations/coupon_db/V1__create_coupon_tables.sql`：建表脚本。
- 根 README「跨模块业务链路」：下单锁券与支付核销链路。
