# support-service 帮助与反馈服务

> 学生反馈工单系统：学生提交、补充、关闭工单；管理端受理、公开回复、内部备注、关闭。默认端口 `9114`，数据库 `support_db`。沟通通过工单时间线消息实现，顾客触达经通知服务同步推送；同时向用户服务提供注销前的未结工单阻断计数。不消费任何 MQ 事件。

## 模块定位与职责边界

- 工单分两类：`DINING` 用餐问题（必须关联本人订单，窗口与餐厅取自订单快照，由窗口/餐厅经理处理）、`SOFTWARE` 软件问题（不关联业务对象，技术侧处理）。
- 时间线消息四类：学生补充（CUSTOMER）、员工公开回复（STAFF）、系统消息（SYSTEM）、员工内部备注（internal_note，学生不可见）。
- 无评价功能；通知经 notification-service 同步 Feign 调用（非事件总线），失败不回滚业务。
- 注销阻断只读：向用户服务返回未关闭工单数，不修改任何状态。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / JDBC / Security / OAuth2 Resource Server（无状态 JWT）/ Validation / Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer（无 MQ 依赖） |
| 数据 | MyBatis-Plus 3.5.8（注解 SQL）、Druid（max-active=5）、MySQL `support_db`（默认 `127.0.0.1:9095`） |
| 缓存 | Redis 仅用于 JWT 注销黑名单（与认证服务共享实例） |
| 共享模块 | common-web（统一响应/全局异常/JWT 身份回查/内部令牌校验）、common-redis |

- Feign 默认 connectTimeout 3000 / readTimeout 5000；无 profile 区分，全部经环境变量覆盖。
- 直连 URL：`file.service-base-url`（图片归属校验，默认 :9102）、`identity.user-service-base-url`（身份版本回查，默认 :9092）。
- 服务间共享令牌 `internal.scope.token`，未配置时内部调用一律拒绝。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `SupportTicketController` | 学生端工单接口（`/support/tickets`），幂等键取 `Idempotency-Key` 头（回退 `X-Request-Key`） |
| `SupportManagementController` | 管理端工单接口（`/support/management/tickets`），构造操作者快照 `SupportActor` |
| `AccountDeletionInternalController` | 内部注销阻断计数接口，共享令牌校验 |
| `SupportPingController` | `GET /support/ping` 匿名存活探测，不碰数据库 |
| `SupportTicketServiceImpl` | 学生侧业务：创建/分页/详情/补充/关闭，幂等与越权防护 |
| `SupportManagementServiceImpl` | 管理端业务：队列/详情/受理/回复/备注/关闭，事务提交后发通知 |
| `SupportScopeService` | 管理端数据范围与类型权限判定（每次实时向用户服务现查范围，不缓存） |
| `SupportAccountDeletionService` | 注销阻断只读统计 |
| `SupportImageService` | 图片键校验（SUPPORT_IMAGE 类型、≤3 张、去重）与 JSON 序列化 |
| `SupportTicketViewAssembler` | DO→VO 转换、中文映射、列表描述截断 |
| `SupportTicketMapper` / `SupportTicketMessageMapper` | 注解 SQL：查询、带原状态条件的变更、按可见性过滤的时间线 |
| `SupportSecurityConfig` / `SupportSecurityErrorHandler` | 无状态 JWT 资源服务器 + 黑名单校验 + 统一 401/403 JSON |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/support_db/V1`（建表）、`V2`（幂等键长度调整）、`V3`（注释修正）。

### support_ticket（反馈工单主表）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 内部顺序主键，仅用于存储与游标分页 |
| ticket_id | CHAR(36) | NOT NULL | uk_support_ticket_id | 公开工单标识（UUID），对外一律使用此列 |
| customer_id | BIGINT | NOT NULL | uk_support_ticket_request；本人分页索引参与 | 提交学生内部用户编号 |
| type | VARCHAR(16) | NOT NULL | — | 工单类型：DINING 用餐 / SOFTWARE 软件 |
| category | VARCHAR(32) | NOT NULL | — | 分类编码，服务端白名单校验 |
| status | VARCHAR(16) | NOT NULL | 状态队列索引参与 | PENDING 待受理 / PROCESSING 处理中 / CLOSED 已关闭 |
| order_id | BIGINT | 可空 | — | 关联订单编号，仅用餐问题 |
| order_no | VARCHAR(32) | 可空 | — | 关联订单号快照，便于展示 |
| window_id | BIGINT | 可空 | 窗口筛选索引参与 | 窗口编号快照，用餐问题取自关联订单；客户端传值必须与订单一致 |
| window_name | VARCHAR(64) | 可空 | — | 窗口名称快照 |
| restaurant_id | BIGINT | 可空 | 餐厅筛选索引参与 | 所属餐厅编号，用于管理端数据范围 |
| description | VARCHAR(1000) | NOT NULL | — | 问题描述（10–1000 字） |
| image_file_keys | JSON | NOT NULL | — | 图片文件键数组，最多 3 个 |
| device_info | VARCHAR(255) | 可空 | — | 软件问题设备信息 |
| customer_unread | TINYINT | NOT NULL，默认 0 | — | 是否有商家新回复：0 否、1 是 |
| request_key | VARCHAR(64) | NOT NULL | uk_support_ticket_request | 创建幂等键（8–64 位） |
| request_hash | CHAR(64) | NOT NULL | — | 创建内容 SHA-256 摘要，识别同键不同内容 |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 创建时间 |
| updated_at | DATETIME(3) | NOT NULL，自动更新 | — | 更新时间 |
| last_reply_at | DATETIME(3) | 可空 | — | 最近商家回复时间 |
| closed_at | DATETIME(3) | 可空 | — | 关闭时间 |

- 唯一键：`uk_support_ticket_id(ticket_id)`、`uk_support_ticket_request(customer_id, request_key)`（创建幂等并发兜底）。
- 普通索引：`idx_support_ticket_customer(customer_id, id)`、`idx_support_ticket_status(status, id)`、`idx_support_ticket_window(window_id, id)`、`idx_support_ticket_restaurant(restaurant_id, id)`。

### support_ticket_message（时间线消息）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 内部顺序主键 |
| message_id | CHAR(36) | NOT NULL | uk_support_message_id | 公开消息标识 |
| ticket_id | BIGINT | NOT NULL | uk_support_message_request；时间线索引参与 | 关联工单内部主键 |
| sender_type | VARCHAR(16) | NOT NULL | — | 发送方：CUSTOMER / STAFF / SYSTEM |
| sender_id | BIGINT | 可空 | — | 发送方内部用户编号，系统消息为空 |
| content | VARCHAR(1000) | NOT NULL | — | 消息正文 |
| image_file_keys | JSON | NOT NULL | — | 图片文件键数组，最多 3 个 |
| internal_note | TINYINT | NOT NULL，默认 0 | — | 是否内部备注：1 学生不可见（SQL 层过滤） |
| request_key | VARCHAR(64) | 可空 | uk_support_message_request | 补充说明幂等键；系统消息为空（唯一键允许多行 NULL） |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 创建时间 |

- 唯一键：`uk_support_message_id(message_id)`、`uk_support_message_request(ticket_id, request_key)`；普通索引：`idx_support_message_ticket(ticket_id, id)`。

## 核心业务流程

### 创建工单

1. 幂等键校验 `^[A-Za-z0-9_-]{8,64}$`，缺失返回 400。
2. 类型与分类白名单一致性校验。
3. 描述 10-1000 字；图片键逐个经文件服务校验归属（上传人 + SUPPORT_IMAGE + 未绑定业务）。
4. 内容 SHA-256 摘要：同幂等键不同内容返回 409。
5. 幂等回查命中直接返回已有工单；用餐问题必须提供订单：透传学生 Bearer 由订单服务校验归属并取窗口/餐厅快照，不接受仅凭窗口编号关联；旧客户端传窗口时只允许与订单快照一致。
6. 插入冲突（唯一键）后回查，按内容摘要决定返回或报错（并发幂等兜底）。

### 状态流转

| 流转 | 触发者 | 规则 |
|---|---|---|
| PENDING → PROCESSING | 仅管理端受理 | 带原状态条件更新；重复受理幂等；已关闭 409 |
| → CLOSED | 学生或管理端 | 双方均可关闭 PENDING/PROCESSING；幂等；写入系统消息（含可选关闭说明） |

无回退路径；CLOSED 后拒绝补充、受理、回复与备注。越权访问统一 404（不泄露存在性），类型越权 403。

### 回复与未读

- 学生补充：幂等回查先于状态校验（首次成功后被关闭，重试仍返回首次结果）。
- 员工公开回复：仅 PROCESSING 可回复；消息落库与置未读同一事务，未读更新失败整体回滚避免孤儿消息；落库后刷新最后回复时间。
- 学生查看详情即清未读（失败仅告警）；学生时间线在 SQL 层过滤内部备注。
- 内部备注：不置未读、不通知、不刷新回复时间。

### 注销阻断

`GET /internal/users/{userId}/account-deletion/blockers`：共享令牌校验后统计 PENDING/PROCESSING 工单数返回；用户服务侧对该依赖 fail-closed（不可用即拒绝注销）。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 工单重复创建 | `request_key` 幂等键 + `request_hash` 内容摘要：先幂等回查，唯一键 `(customer_id, request_key)` 兜底并发插入，冲突后回查并按摘要决定返回或报错 | 同键同内容返回首次工单；同键不同内容 409 |
| 补充消息重复提交 | 消息幂等键 `(ticket_id, request_key)` 唯一键兜底；回查先于状态校验（首次成功后被关闭，重试仍返回首次结果） | 重复请求幂等吸收 |
| 受理与关闭并发 | 状态变更一律带原状态条件更新（如 `PENDING → PROCESSING`）：只有一个赢家 | 重复受理幂等；已关闭后受理/回复/备注均 409 |
| 回复与清未读竞态 | 消息落库与置未读同一事务，未读更新失败整体回滚避免孤儿消息；学生读取详情清未读失败仅告警不阻塞 | 不会出现「有回复但无未读标记」 |
| 并发回复未读覆盖 | `customer_unread` 为布尔标记，任何新回复置 1，学生读取置 0；时间线以 last_reply_at 排序 | 极端并发下未读标记可能被后写覆盖，最终以时间线为准 |
| 内部令牌未配置 | 共享令牌常量时间比较，未配置一律拒绝 | fail-closed，不降级放行 |

## 接口清单

学生端（`/support/tickets`，仅 CUSTOMER）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/support/tickets` | 创建工单（幂等键必需；用餐问题必须关联本人订单） |
| GET | `/support/tickets?cursor=&limit=` | 本人游标分页（limit 默认 20 最大 50） |
| GET | `/support/tickets/{ticketId}` | 详情（读取即清未读，不含内部备注） |
| POST | `/support/tickets/{ticketId}/messages` | 补充说明（幂等键必需） |
| POST | `/support/tickets/{ticketId}/close` | 本人关闭 |

管理端（`/support/management/tickets`，RESTAURANT_MANAGER / GENERAL_MANAGER / SYSTEM_ADMIN）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/support/management/tickets?status=&type=&windowId=&cursor=&limit=` | 队列查询（按角色与数据范围收敛） |
| GET | `/support/management/tickets/{ticketId}` | 详情（含内部备注） |
| POST | `/{ticketId}/accept` | 受理 |
| POST | `/{ticketId}/reply` | 公开回复 |
| POST | `/{ticketId}/notes` | 内部备注 |
| POST | `/{ticketId}/close` | 关闭 |

内部与公共：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/users/{userId}/account-deletion/blockers` | 注销阻断计数（X-Internal-Token） |
| GET | `/support/ping` | 匿名存活探测 |

## 消息、异步与通知设计

- 无 MQ 生产与消费；通知在业务事务提交后（afterCommit）同步调用通知服务内部接口：
  - 回复：`/internal/notifications/support-reply`（类型 SUPPORT_REPLY），eventId=`support-reply:{messageId}`。
  - 受理/关闭：`/internal/notifications/support-status`（类型 SUPPORT_STATUS），eventId 取系统消息 ID。
- 通知服务按（接收人，eventId）幂等；调用失败仅记日志，不影响已提交业务。

## 安全设计

- 管理端数据范围：SYSTEM_ADMIN 仅软件工单（平台队列强制 SOFTWARE）；RESTAURANT_MANAGER 仅授权餐厅的用餐工单；GENERAL_MANAGER 需平台范围标记才看全平台，否则按授权餐厅集合；范围查询失败绝不放行（fail-closed）。
- JWT：RSA 公钥缺失启动失败；验签后要求 jti 并查 Redis 黑名单，Redis 不可用一律拒绝；`JwtUserIdentity` 回查用户服务换内部编号。
- 过滤链：`/support/**` 需认证，`/internal/**` 由令牌校验，其余 `denyAll`。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `SUPPORT_SERVER_PORT` | 9114 | 服务端口 |
| `SUPPORT_DB_URL/USERNAME/PASSWORD` | 127.0.0.1:9095/support_db | 数据库 |
| `INTERNAL_SCOPE_TOKEN` | 必填，无默认值 | 服务间共享令牌 |
| `file.service-base-url` | :9102 | 文件归属校验直连地址 |
| `identity.user-service-base-url` | :9092 | 身份回查直连地址 |
| `REDIS_HOST/PORT/PASSWORD` | 127.0.0.1:9096 | 黑名单 Redis |

## 异常与失败行为

- 全部 Feign 客户端无 fallback：失败按异常映射（订单 401/403/404 映射业务错误，其余 503「订单服务暂不可用」）；范围查询失败不降级放行。
- 通知失败不回滚业务；清未读失败不阻塞详情返回。
- 幂等键同键不同内容 409；越权 404；并发状态冲突按原状态条件更新控制。

## 关键代码入口

`SupportTicketServiceImpl`（创建幂等与上下文填充）→ `SupportManagementServiceImpl`（受理/回复/通知时序）→ `SupportScopeService`（数据范围模型）→ `support_db` 迁移脚本。

## 展示与交互约定

- 工单展示编号：取公开工单标识前 8 位转大写，仅用于展示与口头沟通，不作查询键。
- 窗口经理不可见工单：管理接口对 STALL_MANAGER 返回 403，防止被投诉对象自行处理投诉。
- 未读规则补充：管理端受理与关闭同样将学生侧置为未读；学生本人关闭不给自己制造未读；列表红点文案为「有更新」。
- 关闭说明：原因可空、最长 200 字，仅拼入关闭系统消息，不单独建字段。
- 通知跳转与文案：`biz_id` 存公开工单标识，消息中心跳转工单详情页；回复、受理、关闭三类通知文案由本服务生成（最新文案以代码常量为准）。

## 已知限制与未验证项

- 无评价与满意度收集；通知补偿重放依赖通知服务的未通知事件任务。
- 未完成跨服务联调与真机验收；注销阻断链路的运行验证待执行。

## 关联文档

- `docs/database/migrations/support_db/`：建表与变更脚本。
- `user-service/README.md`：注销资格校验中的阻断项汇聚。
- `notification-service/README.md`：通知幂等与实时推送。
