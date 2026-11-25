# notification-service 通知与消息服务

> 站内通知（订单状态、工单反馈）、顾客与商家的订单聊天、WebSocket 实时提示与一次性票据签发、通知分类偏好。默认端口 `9107`，数据库 `notification_db`。消费订单状态事件落库后经 Redis Pub/Sub 推送轻量信号；聊天授权每次回源订单/用户服务（fail-closed）。默认端口 `9107`。

## 模块定位与职责边界

- 通知与聊天事实都在本库；实时推送只是「有变化」的信号，正文一律由客户端 HTTP 补拉。
- 聊天授权不缓存：订单沟通上下文、用户角色与数据范围每次向订单/用户服务现查，依赖失败一律拒绝，不降级放行。
- 工单通知经内部接口由客服服务同步调用；订单通知经 MQ 消费；两者共用同一通知表与幂等键。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / **WebSocket** / Security / OAuth2 Resource Server、Actuator |
| 数据 | MyBatis-Plus 3.5.8 + Druid + MySQL `notification_db`（默认 `127.0.0.1:9095`） |
| 缓存 | Redis：WebSocket 一次性票据、实时推送 Pub/Sub、聊天发送限流 |
| 消息 | Spring Cloud Stream RocketMQ（仅消费） |
| 定时任务 | `@EnableScheduling`：聊天 Outbox 发布、过期连接清理 |

- `spring.profiles.include: notification-mq`；消费 binding `orderNotification-in-0` → topic `order-status-notification-v1`，消费组 `notification-order-status-v1`；`max-attempts:1` + 错误处理器重抛交 Broker 重投 16 次。
- `notification.chat.enabled` 聊天总开关（关闭时聊天路由不注册、WebSocket 拒绝聊天帧）；`notification.websocket.allowed-origins` 握手 Origin 白名单。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `NotificationController` | 本人通知查询/已读/偏好读写，用户编号一律取自 JWT |
| `ChatController` | 聊天会话/消息/已读/撤回/未读 HTTP 接口（受开关控制） |
| `NotificationInternalController` | 客服服务内部通知入口（`X-Internal-Token` + 幂等） |
| `NotificationSocketTicketController` | WebSocket 一次性票据签发 |
| `OrderNotificationServiceImpl` | 订单事件幂等落库 + 事务提交后推送信号 |
| `SupportNotificationService` | 工单通知落库（按通知偏好决定是否实时提示） |
| `NotificationPreferenceServiceImpl` | 分类偏好稀疏存储与开关判断 |
| `ChatServiceImpl` / `ChatAccessServiceImpl` | 聊天业务与授权事实（订单状态窗口、角色校验） |
| `OrderNotificationConsumer` / `OrderNotificationEvent` | MQ 消费入口与事件契约校验 |
| `ChatOutboxPublisher` | 聊天事件定时发布 |
| `NotificationWebSocketConfig` 等 | 握手拦截、票据校验、连接管理、Pub/Sub 总线 |
| Mapper ×5 | 通知、偏好、会话、消息、聊天 Outbox |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/notification_db/V1–V6`。

### notifications（站内通知）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 内部顺序主键 |
| notification_id | CHAR(36) | NOT NULL | uk_notification_public_id | 公开通知标识 |
| recipient_id | BIGINT | NOT NULL | uk_notification_recipient_event；分页/未读索引参与 | 接收用户内部编号 |
| event_id | VARCHAR(128) | NOT NULL | uk_notification_recipient_event | 来源业务事件标识（MQ 重投与内部调用幂等键） |
| notification_type | VARCHAR(32) | NOT NULL | — | 通知类型（订单状态/工单反馈等） |
| title | VARCHAR(120) | NOT NULL | — | 通知标题 |
| content | VARCHAR(500) | NOT NULL | — | 通知内容 |
| order_id | BIGINT | 可空，默认 NULL | — | 关联订单编号 |
| biz_type | VARCHAR(16) | 可空，默认 NULL | — | 业务类型，SUPPORT 表示反馈工单 |
| biz_id | VARCHAR(64) | 可空，默认 NULL | — | 业务公开标识，配合 biz_type 跳转详情 |
| read_at | DATETIME(3) | 可空，默认 NULL | 未读索引参与列 | 阅读时间，空表示未读 |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 创建时间 |

- 唯一键：`uk_notification_public_id(notification_id)`、`uk_notification_recipient_event(recipient_id, event_id)`。
- 普通索引：`idx_notification_recipient_id(recipient_id, id)`、`idx_notification_recipient_unread(recipient_id, read_at, id)`。

### notification_preferences（通知分类偏好）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| user_id | BIGINT | NOT NULL | 联合主键 | 用户内部编号，与通知接收人一致 |
| category | VARCHAR(32) | NOT NULL | 联合主键 | 通知分类：ORDER 订单、SUPPORT 反馈工单、CHAT 商家聊天 |
| enabled | TINYINT(1) | NOT NULL，默认 1 | — | 1 开启提醒、0 关闭提醒 |
| updated_at | DATETIME(3) | NOT NULL，自动更新 | — | 最后修改时间 |

- 联合主键 `(user_id, category)`；**缺记录 = 默认开启**（稀疏存储）。

### chat_conversations（订单聊天会话）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 会话内部主键 |
| conversation_uuid | CHAR(36) | NOT NULL | uk_chat_uuid | 公开会话 UUID |
| order_id | BIGINT | NOT NULL | uk_chat_order | 关联订单编号，一笔订单唯一会话 |
| order_no | VARCHAR(64) | NOT NULL | — | 订单号快照 |
| window_id | BIGINT | NOT NULL | idx_chat_window | 窗口内部编号 |
| window_name | VARCHAR(120) | NOT NULL | — | 店铺名称快照 |
| customer_id | BIGINT | NOT NULL | idx_chat_customer | 下单顾客内部编号 |
| last_seq | BIGINT | NOT NULL，默认 0 | — | 会话内已提交消息最大序号 |
| last_content | VARCHAR(500) | NOT NULL，默认 '' | — | 最后消息摘要 |
| last_message_at | DATETIME(3) | 可空，默认 NULL | — | 最近消息时间 |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 创建时间 |

- 唯一键：`uk_chat_order(order_id)`、`uk_chat_uuid(conversation_uuid)`；普通索引：`idx_chat_customer(customer_id, id)`、`idx_chat_window(window_id, id)`。

### chat_messages（会话消息）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 消息内部主键 |
| message_uuid | CHAR(36) | NOT NULL | uk_chat_message_uuid | 公开消息 UUID |
| conversation_id | BIGINT | NOT NULL | uk_chat_sequence、uk_chat_idempotency | 会话内部主键 |
| seq | BIGINT | NOT NULL | uk_chat_sequence | 会话内递增序号 |
| sender_id | BIGINT | NOT NULL | uk_chat_idempotency | 实际发送人内部编号 |
| sender_uuid | CHAR(36) | NOT NULL | — | 实际发送人公开 UUID |
| sender_name | VARCHAR(100) | NOT NULL | — | 发送人昵称快照 |
| sender_side | VARCHAR(16) | NOT NULL | — | CUSTOMER 或 MERCHANT |
| client_message_id | CHAR(36) | NOT NULL | uk_chat_idempotency | 客户端幂等消息 UUID |
| content | VARCHAR(500) | NOT NULL | — | 纯文本正文 |
| revoked_at | DATETIME(3) | 可空，默认 NULL | — | 撤回时间，非空时对外屏蔽原文 |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 服务端消息时间 |

- 唯一键：`uk_chat_message_uuid`、`uk_chat_sequence(conversation_id, seq)`、`uk_chat_idempotency(conversation_id, sender_id, client_message_id)`。

### chat_read_state（已读水位）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| conversation_id | BIGINT | NOT NULL | 联合主键 | 会话内部主键 |
| user_id | BIGINT | NOT NULL | 联合主键 | 实际阅读人内部编号 |
| last_read_seq | BIGINT | NOT NULL，默认 0 | — | 最后已读序号，仅递增 |
| updated_at | DATETIME(3) | NOT NULL，自动更新 | — | 阅读更新时间 |

### chat_event_outbox（聊天实时事件投递）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 投递记录内部编号 |
| event_uuid | CHAR(36) | NOT NULL | uk_chat_event | 投递事件 UUID |
| conversation_uuid | CHAR(36) | NOT NULL | — | 需补查的公开会话 UUID |
| attempts | BIGINT | NOT NULL，默认 0 | — | 已尝试次数 |
| next_attempt_at | DATETIME(3) | NOT NULL，默认当前时间 | 待投递扫描索引参与列 | 下次投递时间 |
| delivered_at | DATETIME(3) | 可空，默认 NULL | 待投递扫描索引参与列 | 发布成功时间，不代表客户端已读 |
| created_at | DATETIME(3) | NOT NULL，默认当前时间 | — | 事件创建时间 |

- 唯一键：`uk_chat_event(event_uuid)`；扫描索引：`idx_chat_pending(delivered_at, next_attempt_at, id)`。

**WebSocket 票据**：Redis key `notification:ws-ticket:{ticket}`，32 字节随机数，TTL 30 秒，值为用户/令牌上下文；消费用原子 getAndDelete 防重放；握手子协议 `notification.v1` + `ticket.{ticket}`。

## 核心业务流程

### 订单通知链路

1. 消费 `order-status-notification-v1`：校验事件（版本、eventId 格式、状态白名单）。
2. 落库：唯一键冲突且确认为同一事件则幂等吸收，不同事件抛异常交 Broker 重投。
3. 偏好开启（ORDER 分类）时注册事务提交后推送：Redis Pub/Sub 频道广播用户级变化信号，在线连接收到 `notification.changed` 后 HTTP 补拉。

### 工单通知链路

客服服务事务提交后调用内部接口落库；`(recipient_id,event_id)` 幂等；SUPPORT 分类关闭时只跳过实时提示，消息中心仍可查看。

### 聊天

- 建会话：仅顾客显式发起；授权通过后插入会话（唯一键兜底并发）并写聊天 Outbox。
- 发消息（单事务）：会话 FOR UPDATE 行锁 → 幂等回查（同键不同内容 409）→ 授权校验（已支付/制作中/待取货开放；已完成 24 小时内可发；其余只读）→ Redis 限流（1 秒 2 条 / 1 分钟 30 条）→ 消息（seq=lastSeq+1）+ 会话推进 + Outbox 三写同事务。
- Outbox 每秒发布批次事件 → Pub/Sub 推送 `chat.changed`；逐连接复核会话权限后才推送。
- 已读：水位 `GREATEST` upsert 防回退；CHAT 偏好关闭时未读数返回 0 但水位不动（重新开启后恢复）。
- 撤回：与会话锁互斥；SQL 条件限定本人、未撤回、10 分钟内；撤回末条时同步替换会话摘要。

### WebSocket 连接生命周期

1. 持 JWT 调 `/notifications/socket-ticket` 换 30 秒一次性票据。
2. 握手校验票据（无/过期/重复/存储故障一律拒绝升级）+ Origin 白名单。
3. 连接建立后每 30 秒清理过期、注销或凭据版本失效的连接；每帧处理都重验身份。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 订单事件重复投递 | 通知唯一键 `(recipient_id, event_id)`：落库唯一键冲突时校验是否同一事件——同一事件幂等吸收，不同事件抛异常交 Broker 重投 | 重复消息不产生第二条通知；错配事件不吞掉，留死信排查 |
| 会话消息并发写入 | 会话行 `FOR UPDATE` 锁内完成「幂等回查 → 授权校验 → 限流 → seq 分配（lastSeq+1）→ 消息 + 会话推进 + Outbox」单事务 | 任何一步失败整体回滚；seq 唯一键兜底并发插入 |
| 客户端重复发送 | 幂等键 `(conversation_id, sender_id, client_message_id)`：同键同内容返回已落库消息，同键不同内容 409 | 首次成功后被关闭的会话重试仍返回首次结果 |
| 发送限流 | Redis 计数：每人每秒 2 条 / 每分钟 30 条，业务事务前检查 | 超限直接拒绝当次发送，不占用 seq |
| 已读水位回退 | `GREATEST` upsert 只增不减；CHAT 偏好关闭时未读数显示 0 但水位不动 | 重新开启偏好后未读数恢复，不丢消息 |
| 会话摘要与末条撤回竞态 | 撤回与会话锁互斥：撤回末条时在锁内同步替换会话 last_content/last_message_at | 不会出现「摘要已撤回消息」的窗口 |
| WebSocket 票据重放/并发握手 | 一次性票据原子 getAndDelete（TTL 30 秒）：二次握手拿不到票据即拒绝；Origin 白名单 + 子协议校验 | 过期/重复/存储故障一律拒绝升级 |
| 推送信号丢失 | Redis Pub/Sub 仅广播「有变化」信号，正文由客户端 HTTP 补拉；推送失败只记日志 | 离线/漏收靠补拉收敛，不影响通知事实 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/notifications?cursor=` | 认证 | 游标分页（cursor 属主校验） |
| GET | `/notifications/unread-count` | 认证 | 系统通知未读数（按偏好过滤关闭分类） |
| POST | `/notifications/{id}/read`、`/notifications/read-all` | 认证 | 标记已读 |
| GET/PUT | `/notifications/preferences` | 认证 | 分类偏好读写（ORDER/SUPPORT/CHAT） |
| POST | `/notifications/socket-ticket` | 认证 | 签发 WebSocket 票据（30 秒有效） |
| GET(升级) | `/notifications/ws` | 票据 | WebSocket：客户端帧 `ping`/`chat.send`；服务端帧 `pong`/`chat.ack`/`chat.error`/`notification.changed`/`chat.changed` |
| POST | `/chat/conversations` | 认证 | 按订单建立聊天会话 |
| GET | `/chat/conversations`、`/chat/conversations/{uuid}` | 认证 | 会话列表/详情 |
| GET | `/chat/conversations/{uuid}/messages` | 认证 | 消息分页（beforeSeq/afterSeq 互斥，默认 30 上限 50） |
| POST | `/chat/conversations/{uuid}/read` | 认证 | 上报已读水位 |
| GET | `/chat/unread-count` | 认证 | 聊天未读数 |
| POST | `/chat/conversations/{uuid}/messages/{messageUuid}/revoke` | 认证 | 撤回（10 分钟内） |
| POST | `/chat/conversations/{uuid}/messages/revoked` | 认证 | 批量查询已撤回（≤50） |
| POST | `/internal/notifications/support-reply`、`/support-status` | X-Internal-Token | 工单通知落库 |

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 消费 | `order-status-notification-v1`（订单服务 Outbox 生产） |
| 生产 | 无 MQ 生产；实时提示走 Redis Pub/Sub + 聊天 MySQL Outbox |
| 幂等 | 通知 (recipient_id,event_id) 唯一键；消息幂等键；票据原子消费 |
| 降级 | 推送失败仅告警，DB 通知保留由客户端补拉 |

## 安全设计

- 聊天授权 fail-closed：订单上下文、用户身份任一依赖失败返回服务不可用，不放行。
- WebSocket：一次性票据 + 子协议 + Origin 白名单 + 身份过期/注销即时断开；帧大小 ≤8192，requestId ≤64。
- 内部接口共享令牌；通知查询一律以 JWT 用户编号为准，不接受请求参数指定接收人。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `notification.chat.enabled` | — | 聊天功能总开关 |
| `notification.websocket.allowed-origins` | — | 握手 Origin 白名单 |
| `notification.messaging.enabled` | — | 订单事件消费开关 |
| `internal.scope.token` | — | 服务间共享令牌 |
| `ROCKETMQ_NAMESERVER` | 127.0.0.1:9876 | MQ 地址 |

## 异常与失败行为

- 事件消费异常不本地重试，交 Broker 重投 16 次后进死信；重复投递由唯一键吸收。
- 聊天发送在事务内完成三写，任何一步失败整体回滚；限流直接拒绝当次发送。
- 实时推送为尽力而为：Pub/Sub 无离线保存，离线用户靠补拉。

## 关键代码入口

`OrderNotificationServiceImpl`（事件幂等 + 提交后推送范式）→ `ChatServiceImpl`（会话锁 + 幂等 + 限流）→ `NotificationTicketService` / `NotificationSocketHandler`（票据与连接治理）→ `notification_db` 迁移脚本。

## 渠道筛选与已读回执

- 消息中心支持 `channel=ORDER|SUPPORT` 白名单参数（非法值 400），基于 `biz_type` 过滤实现渠道拆分，不新增数据库列。
- 已读回执：会话详情返回对方阅读水位 `peerReadSeq`（会话列表为 null）；多店主会话取最大水位，店主侧只看顾客水位；迟到的低水位不使已读回退。
- 撤回时限按服务端 UTC+8 时间判定 10 分钟；撤回消息不计入未读数；重复撤回幂等返回成功。
- 客户端兜底：聊天页每 30 秒兜底查询，重连、回前台或进入会话时主动补拉。
- 握手票据携带用户 UUID 与凭据版本，连接建立与每次推送前校验；空闲、注销或凭据失效的旧连接由 30 秒扫描清理；长期令牌不得写入 URL 或日志。

## 已知限制与未验证项

- 实时推送不保证送达，弱网与离线场景完全依赖客户端补拉。
- 聊天已完成订单 24 小时后只读，不可再恢复沟通。
- 运行联调与真机 WebSocket 验收待执行。

## 关联文档

- `support-service/README.md`：工单通知的生产方。
- `order-service/README.md`：订单状态事件 Outbox。
- 根 README「跨模块业务链路」：通知与聊天链路。
