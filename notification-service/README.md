# notification-service 站内通知与商家聊天服务

> 面向顾客保存站内通知，提供列表、未读数、已读、通知偏好和 WebSocket 实时提示；同时承载订单维度的商家与顾客聊天。默认端口 `9107`，数据库 `notification_db`。

## 职责与边界

- 消费订单状态事件 `order-status-notification-v1`，按用户和事件标识幂等写入通知。
- 提供本人通知列表、未读数、单条已读、全部已读和通知偏好设置。
- WebSocket 通道使用短时一次性票据建立连接，数据库提交后再推送在线提示。
- 商家聊天：会话、消息持久化、已读进度、未读数、消息撤回与实时提示。
- 接收反馈工单回复/状态变更的内部通知请求。

**边界：** 通知服务不生成订单事件，订单通知 outbox 在 `order_db`；Redis Pub/Sub 只负责多实例转发在线提示，不保证离线送达。离线数据以通知表和聊天消息表为准。

## 通知链路

1. 订单状态变更与通知 outbox 在订单库事务内保存。
2. 订单服务发布到 `order-status-notification-v1`。
3. 通知服务消费并校验，按事件标识幂等写入 `notifications`。
4. 数据库提交后通过 WebSocket 提示在线用户，前端重新查询列表与未读数。
5. 客户端离线时通过 HTTP 列表补齐，不依赖 WebSocket 历史消息。

通知支持 `biz_type` / `biz_id` 业务引用，客户端可点击跳转到对应订单或其他业务详情。

## 通知分类与偏好

| 分类 | 包含通知 | 关闭效果 |
|---|---|---|
| `ORDER` | 订单支付、制作、待取货、退款结果 | 抑制实时提示与未读角标 |
| `SUPPORT` | 反馈回复与工单状态变化 | 同上 |
| `CHAT` | 商家新消息提醒 | 同上 |

数据库中缺少某分类记录时按开启处理，只有用户显式关闭才落库；关闭只抑制提醒，通知记录与聊天消息本身继续保留。

## WebSocket 认证

- 客户端先用登录态调用 `POST /notifications/socket-ticket` 申请一次性票据。
- 票据有效期短、只能使用一次；握手时校验有效期、黑名单与来源。
- 不把长期 Access Token 放进 WebSocket URL。
- 允许来源由 `NOTIFICATION_WEBSOCKET_ALLOWED_ORIGINS` 配置，默认覆盖本地 Nginx 8088 与开发服务器 5173。

## 商家聊天

- `POST /chat/conversations` 按订单建立或获取会话；只有支付成功后的订单可建立，顾客只能进入自己的订单。
- 商家必须是订单所属窗口的当前管理者，权限通过订单服务 `/internal/orders/{orderId}/chat-context` 核对。
- 会话内消息使用公开 UUID、顺序号和客户端幂等键，重复发送不会产生多条消息。
- 支持消息列表分页、已读进度、未读总数、消息撤回与撤回通知。
- 消息先持久化再推送；`chat_event_outbox` 保证变更事件可靠发布，Redis Pub/Sub 用于多实例实时转发。
- 顾客端界面在 uni-app，商家端界面属于独立 Vue 管理端；完整端到端验收仍需部署验证。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `GET /notifications` | 通知列表 |
| `GET /notifications/unread-count` | 未读数 |
| `POST /notifications/{notificationId}/read` | 单条已读 |
| `POST /notifications/read-all` | 全部已读 |
| `GET /notifications/preferences` | 通知偏好 |
| `PUT /notifications/preferences` | 更新偏好 |
| `POST /notifications/socket-ticket` | 申请 WebSocket 一次性票据 |
| `POST /chat/conversations` | 获取/创建订单会话 |
| `GET /chat/conversations` | 会话列表 |
| `GET /chat/conversations/{uuid}` | 会话详情 |
| `GET /chat/conversations/{uuid}/messages` | 消息分页 |
| `POST /chat/conversations/{uuid}/read` | 标记已读 |
| `GET /chat/unread-count` | 聊天未读数 |
| `POST /chat/conversations/{uuid}/messages/{messageUuid}/revoke` | 撤回消息 |
| `POST /internal/notifications/support-reply` | 反馈回复通知（内部） |
| `POST /internal/notifications/support-status` | 工单状态通知（内部） |

## 数据表

| 表 | 作用 |
|---|---|
| `notifications` | 站内通知：用户、类型、标题、内容、业务引用、已读状态 |
| `notification_preferences` | 用户通知分类开关 |
| `chat_conversations` | 订单聊天会话：订单、窗口、顾客、商家参与方、状态 |
| `chat_messages` | 聊天消息：会话内序号、发送者、内容、幂等键、撤回状态 |
| `chat_event_outbox` | 聊天事件本地消息表 |

迁移脚本位于 [`docs/database/migrations/notification_db`](../docs/database/migrations/notification_db)。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`NotificationController.java`](src/main/java/com/hbue/ordering/notification/controller/NotificationController.java) | 通知接口 |
| [`NotificationSocketTicketController.java`](src/main/java/com/hbue/ordering/notification/controller/NotificationSocketTicketController.java) | WebSocket 票据 |
| [`ChatController.java`](src/main/java/com/hbue/ordering/notification/controller/ChatController.java) | 聊天接口 |
| [`NotificationServiceImpl.java`](src/main/java/com/hbue/ordering/notification/service/impl/NotificationServiceImpl.java) | 通知读写 |
| [`OrderNotificationConsumer.java`](src/main/java/com/hbue/ordering/notification/messaging/OrderNotificationConsumer.java) | 订单事件消费 |
| [`OrderNotificationServiceImpl.java`](src/main/java/com/hbue/ordering/notification/service/impl/OrderNotificationServiceImpl.java) | 订单通知写入 |
| [`ChatServiceImpl.java`](src/main/java/com/hbue/ordering/notification/service/impl/ChatServiceImpl.java) | 聊天业务 |
| [`ChatAccessServiceImpl.java`](src/main/java/com/hbue/ordering/notification/service/impl/ChatAccessServiceImpl.java) | 会话权限 |
| [`NotificationWebSocketConfig.java`](src/main/java/com/hbue/ordering/notification/config/NotificationWebSocketConfig.java) | WebSocket 配置 |
| [`NotificationSocketHandler.java`](src/main/java/com/hbue/ordering/notification/websocket/NotificationSocketHandler.java) | 在线推送 |
| [`NotificationSocketBus.java`](src/main/java/com/hbue/ordering/notification/websocket/NotificationSocketBus.java) | 多实例 Pub/Sub |
| [`NotificationPreferenceServiceImpl.java`](src/main/java/com/hbue/ordering/notification/service/impl/NotificationPreferenceServiceImpl.java) | 通知偏好 |

## 配置与排查

- 订单通知开关 `NOTIFICATION_ORDER_EVENTS_ENABLED`、取消通知 TCC 开关 `NOTIFICATION_CANCEL_TCC_ENABLED` 在订单服务侧，默认随 `ranking-mq` 启用，可被环境变量覆盖。
- 通知未送达：检查订单 outbox、MQ 消费组 `notification-order-status-v1`、通知表记录与 WebSocket 连接。
- 通知已落库但页面不刷新：检查票据、允许来源、Redis Pub/Sub 与前端重连补拉。
- 详细设计见 [`docs/NOTIFICATION_SERVICE.md`](../docs/NOTIFICATION_SERVICE.md)。
