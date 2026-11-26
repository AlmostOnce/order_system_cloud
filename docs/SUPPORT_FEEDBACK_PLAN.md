# 帮助与反馈（学生端）详细执行计划

更新时间：2026-11-23
状态：待确认后实施
范围：后端完整实现（含管理端接口），学生端完整实现；`E:\oreder_sys_cloud_admin` 零改动。

## 1. 背景与目标

学生目前遇到两类问题没有统一入口：

1. **用餐问题**：卫生异物、出餐错误、服务态度、等待过久、分量或价格异常等，需要食堂侧处理。
2. **软件问题**：闪退卡顿、支付异常、下单异常、页面显示、登录账号等，需要技术侧处理。

本计划建设统一的「帮助与反馈」入口和工单能力：学生提交、查看进度、补充说明、关闭；食堂角色和技术角色在各自数据范围内受理与回复。首期只接入 uni-app 学生端，管理端页面后续再接入同一批后端接口。

## 2. 已确认口径

- 统一入口，两类工单：用餐问题 `DINING`、软件问题 `SOFTWARE`。
- 用餐问题可关联订单，也可不关联订单、只选窗口；两种都支持。
- 首期只做学生端；后端可以完整实现管理端处理接口，但不接前端管理端。
- 用餐问题由餐厅经理、总经理等管理人员处理；窗口工作人员不参与工单处理，也不开放工单可见范围，避免被投诉对象自行处理。
- 反馈只做「记录 → 受理 → 回复 → 补充 → 关闭」，不触发退款、不修改订单状态。
- 退款、漏发、金额问题继续走现有「退款与售后」；公开体验表达继续走评价。

## 3. 范围

### 3.1 首期做

- 新增 `support-service` 微服务与 `support_db` 数据库。
- 学生接口：提交、我的列表、详情、补充说明、关闭。
- 管理接口（仅后端）：队列、详情、受理、公开回复、内部备注、关闭。
- 商家回复后写入站内通知，复用现有消息中心与未读红点。
- uni-app 学生端：入口、提交页、列表页、详情页。
- 开发环境数据库迁移执行与结果核验。

### 3.2 不做

- 管理端前端页面、管理端登录与角色入口调整。
- 工单触发退款、补偿、扣款或订单状态变更。
- 实时客服 IM、电话回访、匿名反馈。
- 工单统计报表、满意度评价、自动派单。
- 修改 uni-app 现有的非学生临时调试功能。

## 4. 业务模型

### 4.1 工单类型与分类

| 类型 | 编码 | 分类编码与文案 | 关联对象 |
|---|---|---|---|
| 用餐问题 | `DINING` | `HYGIENE` 卫生/异物、`WRONG_ITEM` 出餐错误、`ATTITUDE` 服务态度、`WAIT_TIME` 等待过久、`PORTION_PRICE` 分量/价格、`OTHER` 其他 | 订单或窗口，至少一个 |
| 软件问题 | `SOFTWARE` | `CRASH` 闪退/卡顿、`PAYMENT` 支付异常、`ORDER` 下单/订单异常、`DISPLAY` 页面显示、`ACCOUNT` 登录/账号、`OTHER` 其他 | 不关联订单与窗口，自动附设备信息 |

分类编码由后端白名单校验，文案由前端映射，后端 VO 同时返回 `categoryLabel`，避免客户端版本不一致导致空白。

### 4.2 状态机

```text
PENDING（待受理）
   │ 管理端受理
   ▼
PROCESSING（处理中）
   │ 学生或管理端关闭
   ▼
CLOSED（已关闭）
```

- 学生提交后为 `PENDING`。
- 管理端首次受理置为 `PROCESSING`，同时写一条 `SYSTEM` 时间线消息。
- 商家回复时状态保持 `PROCESSING`，并置 `customer_unread = 1`。
- 管理端受理与关闭同样置 `customer_unread = 1`；学生本人关闭置 0，不给自己制造未读（见 §12.2）。
- 学生查看详情后 `customer_unread = 0`。
- 只有 `PENDING` / `PROCESSING` 可关闭；重复关闭按幂等成功返回，不报错。
- 所有状态变更使用条件更新，禁止先查后改造成并发覆盖。

### 4.3 与退款、评价的边界

- 退款与售后入口保持原样，不把工单当退款申请。
- 工单详情只展示关联订单号与窗口，不提供退款按钮。
- 评价仍按订单与菜品维度公开表达，工单内容不对外公开。

## 5. 数据库设计

数据库：`support_db`，字符集 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`。
迁移脚本：`docs/database/migrations/support_db/V1__create_support_ticket_tables.sql`。
脚本内置 `support_service` 账号与最小库权限，使用 `CREATE TABLE IF NOT EXISTS`，可重复执行。

### 5.1 support_ticket（工单主表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 内部主键，只用于存储与游标排序 |
| `ticket_id` | CHAR(36) | 公开工单标识，UUID |
| `customer_id` | BIGINT | 提交学生内部用户编号，取自 JWT `sub` |
| `type` | VARCHAR(16) | `DINING` / `SOFTWARE` |
| `category` | VARCHAR(32) | 分类编码，服务端白名单校验 |
| `status` | VARCHAR(16) | `PENDING` / `PROCESSING` / `CLOSED` |
| `order_id` | BIGINT NULL | 关联订单编号，仅用餐问题 |
| `order_no` | VARCHAR(32) NULL | 关联订单号快照，便于展示 |
| `window_id` | BIGINT NULL | 窗口编号，用餐问题必填（由订单反查或学生选择） |
| `window_name` | VARCHAR(64) NULL | 窗口名称快照 |
| `restaurant_id` | BIGINT NULL | 所属餐厅，由 product-service 反查，用于管理端数据范围 |
| `description` | VARCHAR(1000) | 问题描述 |
| `image_file_keys` | JSON | 图片文件键数组，最多 3 个 |
| `device_info` | VARCHAR(255) NULL | 软件问题自动上报的设备信息 |
| `customer_unread` | TINYINT | 是否有商家新回复，0/1 |
| `request_key` | CHAR(36) | 创建幂等键 |
| `request_hash` | CHAR(64) | 创建内容摘要，用于识别同键不同内容 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 最近更新时间 |
| `last_reply_at` | DATETIME(3) NULL | 最近商家回复时间 |
| `closed_at` | DATETIME(3) NULL | 关闭时间 |

约束与索引：

- `UNIQUE KEY uk_support_ticket_id (ticket_id)`
- `UNIQUE KEY uk_support_ticket_request (customer_id, request_key)`
- `KEY idx_support_ticket_customer (customer_id, id)`
- `KEY idx_support_ticket_status (status, id)`
- `KEY idx_support_ticket_window (window_id, id)`
- `KEY idx_support_ticket_restaurant (restaurant_id, id)`

### 5.2 support_ticket_message（工单消息表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | 内部主键 |
| `message_id` | CHAR(36) | 公开消息标识 |
| `ticket_id` | BIGINT | 关联工单内部主键 |
| `sender_type` | VARCHAR(16) | `CUSTOMER` / `STAFF` / `SYSTEM` |
| `sender_id` | BIGINT NULL | 学生或员工内部编号；系统消息为空 |
| `content` | VARCHAR(1000) | 消息正文 |
| `image_file_keys` | JSON | 消息图片文件键数组，最多 3 个 |
| `internal_note` | TINYINT | 1 表示内部备注，学生接口永不可见 |
| `request_key` | CHAR(36) NULL | 补充说明幂等键；系统消息为空 |
| `created_at` | DATETIME(3) | 创建时间 |

约束与索引：

- `UNIQUE KEY uk_support_message_id (message_id)`
- `UNIQUE KEY uk_support_message_request (ticket_id, request_key)`
- `KEY idx_support_message_ticket (ticket_id, id)`

### 5.3 DDL 草案

```sql
CREATE DATABASE IF NOT EXISTS support_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 开发环境服务账号；换环境时按实际密码调整。
CREATE USER IF NOT EXISTS 'support_service'@'%' IDENTIFIED BY 'support_password_dev_2026';
GRANT ALL PRIVILEGES ON support_db.* TO 'support_service'@'%';
FLUSH PRIVILEGES;

USE support_db;

CREATE TABLE IF NOT EXISTS support_ticket
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    ticket_id       CHAR(36)     NOT NULL COMMENT '公开工单标识',
    customer_id     BIGINT       NOT NULL COMMENT '提交学生内部用户编号',
    type            VARCHAR(16)  NOT NULL COMMENT '工单类型：DINING/SOFTWARE',
    category        VARCHAR(32)  NOT NULL COMMENT '分类编码',
    status          VARCHAR(16)  NOT NULL COMMENT '状态：PENDING/PROCESSING/CLOSED',
    order_id        BIGINT       NULL COMMENT '关联订单编号',
    order_no        VARCHAR(32)  NULL COMMENT '关联订单号快照',
    window_id       BIGINT       NULL COMMENT '窗口编号',
    window_name     VARCHAR(64)  NULL COMMENT '窗口名称快照',
    restaurant_id   BIGINT       NULL COMMENT '所属餐厅编号',
    description     VARCHAR(1000) NOT NULL COMMENT '问题描述',
    image_file_keys JSON         NOT NULL COMMENT '图片文件键数组',
    device_info     VARCHAR(255) NULL COMMENT '软件问题设备信息',
    customer_unread TINYINT      NOT NULL DEFAULT 0 COMMENT '是否有商家新回复',
    request_key     CHAR(36)     NOT NULL COMMENT '创建幂等键',
    request_hash    CHAR(64)     NOT NULL COMMENT '创建内容摘要',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    last_reply_at   DATETIME(3)  NULL COMMENT '最近商家回复时间',
    closed_at       DATETIME(3)  NULL COMMENT '关闭时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_ticket_id (ticket_id),
    UNIQUE KEY uk_support_ticket_request (customer_id, request_key),
    KEY idx_support_ticket_customer (customer_id, id),
    KEY idx_support_ticket_status (status, id),
    KEY idx_support_ticket_window (window_id, id),
    KEY idx_support_ticket_restaurant (restaurant_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='学生反馈工单';

CREATE TABLE IF NOT EXISTS support_ticket_message
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    message_id      CHAR(36)      NOT NULL COMMENT '公开消息标识',
    ticket_id       BIGINT        NOT NULL COMMENT '关联工单内部主键',
    sender_type     VARCHAR(16)   NOT NULL COMMENT '发送方：CUSTOMER/STAFF/SYSTEM',
    sender_id       BIGINT        NULL COMMENT '发送方内部用户编号',
    content         VARCHAR(1000) NOT NULL COMMENT '消息正文',
    image_file_keys JSON          NOT NULL COMMENT '图片文件键数组',
    internal_note   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否内部备注',
    request_key     CHAR(36)      NULL COMMENT '补充说明幂等键',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_message_id (message_id),
    UNIQUE KEY uk_support_message_request (ticket_id, request_key),
    KEY idx_support_message_ticket (ticket_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工单时间线消息';
```

## 6. 后端服务

### 6.1 模块与配置

- 新增模块 `support-service`，包名 `com.hbue.ordering.support`，默认端口 `9108`。
- 不新建 `support-api`：当前没有其他 Java 服务需要复用工单契约，管理端通过网关 HTTP 调用。
- 根 `pom.xml` 增加 `<module>support-service</module>`。
- 网关：`/api/support/**` 转发到 `lb://support-service`，`StripPrefix=1`；增加 `/api/support/v3/api-docs` 文档路由。
- Swagger 聚合：`gateway-service` 的 `springdoc.swagger-ui.urls` 增加 `support-service`。
- 安全：JWT 资源服务器，校验签名、issuer、有效期、jti 黑名单与身份版本；`@EnableMethodSecurity` 开启方法级角色校验。
- 数据库、Redis、Nacos、OpenFeign 超时配置参照 `comment-service`。
- `internal.scope.token`、`file.service-base-url`、`identity.user-service-base-url` 沿用现有环境变量。

### 6.2 包结构与文件清单

```text
support-service/
  pom.xml
  src/main/resources/application.yml
  src/main/java/com/hbue/ordering/support/
    SupportServiceApplication.java
    config/
      SupportSecurityConfig.java
      SupportSecurityErrorHandler.java
    controller/
      SupportTicketController.java            学生接口
      SupportManagementController.java        管理接口
    service/
      SupportTicketService.java
      SupportManagementService.java
      impl/
        SupportTicketServiceImpl.java
        SupportManagementServiceImpl.java
        SupportTicketViewAssembler.java
        SupportScopeService.java
    repository/
      SupportTicketRepository.java
      SupportTicketMessageRepository.java
    model/
      SupportTicketDO.java
      SupportTicketMessageDO.java
    dto/
      CreateSupportTicketRequest.java
      AddSupportMessageRequest.java
      SupportReplyRequest.java
      SupportInternalNoteRequest.java
      SupportCloseRequest.java
    vo/
      SupportTicketSummaryVO.java
      SupportTicketDetailVO.java
      SupportTicketMessageVO.java
      SupportTicketPageVO.java
      SupportManagementTicketVO.java
    client/
      SupportOrderClient.java                透传学生 JWT 查询订单
      SupportOrderSnapshotVO.java            本地反序列化契约
    support/
      SupportType.java
      SupportCategory.java
      SupportStatus.java
      SupportSenderType.java
```

所有新增类、接口、方法和重要字段编写中文 Javadoc；权限、事务、幂等、并发、过期与异常处理补充中文行内注释。

### 6.3 学生接口契约

所有接口要求 `CUSTOMER` 角色，用户编号取自 JWT `sub`。

#### 6.3.1 提交工单

`POST /support/tickets`，请求头 `Idempotency-Key` 必填（8-64 位，字母数字、下划线、短横线）。

```json
{
  "type": "DINING",
  "category": "HYGIENE",
  "orderId": 10086,
  "windowId": null,
  "description": "餐里发现异物，希望核实处理",
  "imageFileKeys": ["a1b2c3d4e5f60718293a4b5c6d7e8f90"],
  "deviceInfo": ""
}
```

校验规则：

- `type`、`category`、`description` 必填；描述去首尾空白后 10-1000 字。
- `imageFileKeys` 最多 3 个，只接受 32 位十六进制文件键，且通过 `ManagedImageClient.requireImageKey(key, userId, "SUPPORT_IMAGE", null)` 校验归属、业务类型与图片类型。
- `DINING`：`orderId` 与 `windowId` 至少一个。
  - 有 `orderId`：携带学生自己的 `Authorization` 调用 order-service `GET /orders/{orderId}`，由订单服务校验归属；取订单的 `windowId`、`orderNo`、`windowName`，再经 product-service 内部接口换出 `restaurantId`。
  - 只有 `windowId`：经 product-service 内部接口确认窗口存在，取 `windowName` 与 `restaurantId`。
  - 两者同时存在时，订单窗口优先；窗口不一致返回 400。
- `SOFTWARE`：忽略 `orderId` 与 `windowId`；`deviceInfo` 截断到 255 字后保存。
- 幂等：同 `customer_id + request_key` 且 `request_hash` 相同，返回首次创建的详情；内容不同返回 409「请求标识已用于其他反馈内容」。
- 返回 `SupportTicketDetailVO`，HTTP 200，业务码沿用 `COMMON_000`。

#### 6.3.2 我的工单列表

`GET /support/tickets?cursor=&limit=20`

- 按 `id DESC` 游标分页，只返回本人数据。
- `limit` 最大 50；额外取一条判断 `hasMore`。
- 返回 `SupportTicketPageVO`：`items`、`nextCursor`、`hasMore`。
- 列表项字段：`ticketId`、`displayNo`、`type`、`typeLabel`、`categoryLabel`、`status`、`statusLabel`、`orderNo`、`windowName`、`description`（截断 60 字）、`customerUnread`、`createdAt`、`lastReplyAt`。
- `displayNo` 由 `ticketId` 前 8 位大写生成，仅用于展示与口头沟通，不作为查询键。

#### 6.3.3 工单详情

`GET /support/tickets/{ticketId}`

- 只允许本人；不存在与他人统一返回 404「反馈不存在」。
- 返回工单全部字段、图片展示地址（`/api/files/{key}/content`）与时间线。
- 时间线只返回 `internal_note = 0` 的消息，按 `id ASC`。
- 查看成功后异步或同事务将 `customer_unread` 清零；清零失败不影响详情返回，下次查看继续清零。

#### 6.3.4 补充说明

`POST /support/tickets/{ticketId}/messages`，请求头 `Idempotency-Key` 必填。

```json
{
  "content": "补充：事发时间大约是中午 12 点",
  "imageFileKeys": []
}
```

- 仅本人、且状态为 `PENDING` / `PROCESSING`；`CLOSED` 返回 409「反馈已关闭，不能继续补充」。
- 内容 1-1000 字；图片最多 3 张，校验规则同提交。
- 消息写入 `sender_type = CUSTOMER`、`internal_note = 0`；同一 `ticket_id + request_key` 重放返回首次结果。
- 更新工单 `updated_at`；状态不变。

#### 6.3.5 关闭工单

`POST /support/tickets/{ticketId}/close`

```json
{ "reason": "问题已解决" }
```

- 仅本人；`PENDING` / `PROCESSING` 可关闭，`CLOSED` 直接返回成功（幂等）。
- 条件更新 `status = 'CLOSED', closed_at = NOW(3), customer_unread = 0 WHERE id = ? AND customer_id = ? AND status IN ('PENDING','PROCESSING')`。
- 写入一条 `SYSTEM` 消息「学生已关闭反馈」。
- `reason` 可空，最长 200 字，仅用于系统消息内容，不单独建字段。

### 6.4 管理接口契约（仅后端，不接管理端前端）

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/support/management/tickets` | 见 6.5 | 队列查询，支持 `status`、`type`、`windowId`、`cursor`、`limit` |
| GET | `/support/management/tickets/{ticketId}` | 见 6.5 | 详情，含内部备注 |
| POST | `/support/management/tickets/{ticketId}/accept` | 见 6.5 | 受理，PENDING → PROCESSING |
| POST | `/support/management/tickets/{ticketId}/reply` | 见 6.5 | 公开回复，写 STAFF 消息并通知学生 |
| POST | `/support/management/tickets/{ticketId}/notes` | 见 6.5 | 内部备注，学生不可见 |
| POST | `/support/management/tickets/{ticketId}/close` | 见 6.5 | 关闭并写系统消息 |

- 队列默认按 `id DESC`；`status` 为空时返回全部未关闭与已关闭数据，由前端筛选。
- 管理端详情额外返回 `customerId`、内部备注消息、`restaurantId`。
- 受理、回复、关闭均使用条件更新；重复受理返回当前状态而不重复写消息。
- 公开回复会更新 `last_reply_at`、`customer_unread = 1`，并调用通知服务。
- 内部备注不更新 `last_reply_at`、不置未读、不发送通知。
- 管理端回复内容 1-1000 字，图片最多 3 张；图片同样要求上传者属于当前操作员工，`bizType = "SUPPORT_IMAGE"`。

### 6.5 权限与数据范围

| 角色 | 用餐工单 | 软件工单 |
|---|---|---|
| `CUSTOMER` | 仅本人提交的工单 | 仅本人提交的工单 |
| `STALL_MANAGER` | 不可见（接口返回 403） | 不可见 |
| `RESTAURANT_MANAGER` | 仅本人餐厅范围（含下属窗口） | 不可见 |
| `GENERAL_MANAGER` | 全平台 | 全平台 |
| `SYSTEM_ADMIN` | 不可见 | 全平台软件工单 |

- 窗口工作人员是服务提供方，若允许其处理针对本窗口的投诉，会出现被投诉对象自行答复、自行关闭的问题；因此工单处理上移到餐厅经理及总经理。
- 窗口工作人员如需配合核实，由餐厅经理线下转达，不向其开放工单接口与数据。
- 范围事实来自 user-service `UserScopeInternalClient`：`platform`、`restaurantIds`；工单处理不使用 `windowIds` 作为授权范围，`window_id` 只用于按窗口筛选。
- 范围查询失败返回 503，不默认放行、不解释为空范围。
- 越权访问与他人不存在统一返回 404，避免泄露工单存在性。

### 6.6 跨服务调用

| 依赖 | 用途 | 失败策略 |
|---|---|---|
| order-service `GET /orders/{orderId}` | 校验订单归属，取 `windowId`、`orderNo`、`windowName` | 401/403/404 按无权限或不存在拒绝；超时返回 503 |
| product-service `GET /internal/windows/{windowId}` | 确认窗口存在，取 `windowName`、`restaurantId` | 窗口不存在返回 400；超时返回 503 |
| user-service `GET /internal/users/{userId}/scopes` | 管理端数据范围 | 失败返回 503，拒绝受保护操作 |
| notification-service `POST /internal/notifications/support-reply` | 站内通知 | 失败只记录日志，不回滚回复；用户可通过详情查看 |

- 订单查询透传学生自己的 `Authorization`，不使用内部令牌代替用户身份。
- product-service 与 user-service 调用使用 `X-Internal-Token`，该密钥未配置时拒绝调用。
- Feign 统一配置连接 3 秒、读取 5 秒；不在业务代码里无限重试。

### 6.7 图片

- 前端上传时 `bizType = SUPPORT_IMAGE`，`bizRef` 不传。
- 后端只保存文件键，展示地址由 `/api/files/{key}/content` 派生。
- 每张图片必须由当前用户上传、类型为图片、未被删除；校验失败返回 400/403。
- 工单创建与补充说明分别最多 3 张；编辑不涉及图片替换，避免额外状态。

### 6.8 站内通知

在 `notification-service` 增加：

- `NotificationInternalController`
  - `POST /internal/notifications/support-reply`：商家公开回复后提醒学生有新回复。
  - `POST /internal/notifications/support-status`：受理或关闭提交后提醒学生状态已变化。
  - 请求头 `X-Internal-Token`，使用现有 `InternalScopeTokenVerifier` 校验。
  - 请求体：`recipientId`、`eventId`（回复为 `support-reply:{messageId}`，状态为
    `support-status:{messageId}`）、`title`、`content`。
- `NotificationMapper` 增加插入方法：回复记录 `notification_type = 'SUPPORT_REPLY'`，
  状态变更记录 `notification_type = 'SUPPORT_STATUS'`，两者 `order_id = NULL`。
- 通知落库事务提交后调用 `NotificationSocketBus.publish(recipientId)`；推送失败只记录日志。
- 复用现有未读计数与消息中心；`notifications` 表增加可空列 `biz_type`、`biz_id`
  （迁移 `V4__add_notification_business_reference.sql`）。工单通知写入 `SUPPORT` 与公开工单标识，
  消息中心据此从通知气泡跳转 `/pages/support/detail?ticketId=...`；订单通知继续使用 `order_id`。
- `NotificationSecurityConfig` 放行 `/internal/**`，由控制器内共享密钥校验；网关不配置该路径。

学生端通知文案示例：

- 回复标题：`反馈处理进展`；内容：`您提交的反馈（编号 A1B2C3D4）已有新回复，请在“帮助与反馈”中查看。`
- 受理标题：`反馈处理进展`；内容：`您提交的反馈（编号 A1B2C3D4）已由工作人员受理，正在处理中。`
- 关闭标题：`反馈处理进展`；内容含关闭原因（如有），与系统时间线消息一致。

### 6.9 幂等、并发与异常

- 创建工单与补充说明均要求 `Idempotency-Key`；缺失或超长返回 400。
- 唯一键冲突时回查 `(customer_id, request_key)` 或 `(ticket_id, request_key)`：
  - 内容摘要一致：返回首次结果；
  - 内容摘要不一致：409。
- 状态变更条件更新，受影响行数为 0 时重新读取并给出 409 或幂等成功。
- 详情与消息写入同一事务；通知在提交后发送。
- `CLOSED` 工单禁止补充说明、禁止再次受理/回复；内部备注也禁止，避免关闭后继续处理。
- 所有异常返回统一响应体，文案可直接展示，不返回数据库或堆栈信息。

## 7. 学生端（uni-app）方案

### 7.1 文件清单

```text
e:\order_system_cloud_user\
  api\support.uts                      新增：工单接口与强类型解析
  pages\support\submit.uvue            新增：提交反馈
  pages\support\list.uvue              新增：我的反馈
  pages\support\detail.uvue            新增：反馈详情
  styles\support.css                   新增：三个页面共用样式
  pages.json                           修改：注册路由
  pages\mine\mine.uvue                 修改：新增入口
```

### 7.2 入口

在「我的」页「账号与安全」分组下方新增：

- 分组标题：`帮助与支持`
- 单行入口：`帮助与反馈`
- 右侧箭头，进入 `pages/support/list`
- 图标使用 CSS 绘制的圆环问号，不新增图片资源
- 仅 `CUSTOMER` 可见

### 7.3 提交页 `pages/support/submit.uvue`

页面骨架沿用 `account-page account-secondary`，使用共享导航、状态栏与下拉刷新。

顶部类型切换：

- 分段控件：`用餐问题` / `软件问题`
- 切换类型时保留描述与图片，清空不适用的订单/窗口选择；分类重置为默认第一项

用餐问题表单：

1. 关联订单（必选）：用餐问题只接受本人订单，订单是真实消费凭证
   - 调用 `fetchOrders('all', '')` 取最近一页订单
   - 以卡片列表展示订单号、窗口、状态、下单时间
   - 只允许选择本人订单；选中后展示订单号与窗口
   - 服务端按学生 JWT 校验订单归属，并以订单快照确定窗口与餐厅
2. 分类：横向可换行标签
3. 描述：`textarea`，10-1000 字，实时字数
4. 图片：最多 3 张，复用 `chooseLocalImage()` 与 `uploadImage(path, 'SUPPORT_IMAGE', 0)`
5. 提示：`退款、漏发和金额问题请前往订单详情使用“退款与售后”`

软件问题表单：

1. 分类标签
2. 描述：10-1000 字
3. 图片：最多 3 张
4. 自动设备信息：`uni.getSystemInfoSync()` 组装品牌、型号、系统名称、系统版本、App 版本，只读展示
5. 提示：`请勿填写密码、验证码或支付信息`

提交：

- 客户端生成幂等键并写入本地草稿；失败保留同一键重试
- 提交中禁用返回、图片与提交按钮
- 成功跳转详情页并清理草稿
- 失败展示后端文案，不自动重复提交

### 7.4 列表页 `pages/support/list.uvue`

- 标题：`帮助与反馈`
- 顶部按钮：`我要反馈`，进入提交页
- 列表卡片：
  - 第一行：类型标签 + 分类文案 + 状态标签
  - 第二行：窗口名或订单号
  - 第三行：描述摘要，最多两行
  - 底部：提交时间 + `有更新` 红点（覆盖回复、受理、关闭）
- 空态：`还没有提交过反馈`
- 下拉刷新、触底加载、失败重试
- 从详情页返回时刷新列表

### 7.5 详情页 `pages/support/detail.uvue`

- 顶部状态区：状态文案、反馈编号、提交时间
- 问题信息卡：类型、分类、关联订单/窗口、设备信息（软件问题）
- 描述与图片：图片点击可预览
- 时间线：
  - 学生消息靠右，商家消息靠左，系统消息居中灰字
  - 内部备注不展示
- 补充区域：仅 `PENDING` / `PROCESSING` 显示
  - 描述输入 + 最多 3 图 + 提交按钮
  - 使用新的幂等键，成功后清空输入并刷新时间线
- 关闭工单：仅未关闭时显示，二次确认后调用关闭接口
- `CLOSED`：只读，展示关闭时间，隐藏补充与关闭按钮
- 下拉刷新；页面重新显示时刷新一次

### 7.6 `api/support.uts`

- 强类型：`SupportTicket`、`SupportTicketDetail`、`SupportMessage`、`SupportPage`
- 方法：
  - `createSupportTicket(body, key)`
  - `fetchSupportTickets(cursor)`
  - `fetchSupportTicket(ticketId)`
  - `addSupportMessage(ticketId, body, key)`
  - `closeSupportTicket(ticketId, reason)`
- 解析沿用 `jsonString/jsonNumber/jsonBoolean/jsonArray`，缺失字段给类型安全默认值
- 图片地址统一由文件键派生，不信任后端以外的主机地址
- 所有方法返回 `HttpResult` 或收敛后的结构化结果，不抛异常

### 7.7 样式与文案

- 色板沿用：底色 `#FDF6F0`、卡片 `#FFFFFF`、描边 `#F1E5DA`、主色 `#E4572E`、强调 `#C2410C`、正文 `#211C18`、次要文字 `#7A6C60`
- 状态标签：待受理暖黄、处理中主色浅底、已关闭灰底
- 按钮静态 class 以 `button-text button-text-XX` 结尾
- 不出现开发用语、接口名、英文状态码或内部字段名

## 8. 实施步骤

### 阶段 0：准备与核对

- [ ] 确认本文档的分类、角色边界与通知文案。
- [ ] 核对本地 `support_db` 是否已存在，避免误建或覆盖。
- [ ] 核对 `support_service` 账号是否已存在，确认密码策略。
- [ ] 确认网关 Nacos 在线配置的发布方式（仓库文件不会自动生效）。

### 阶段 1：数据库

- [ ] 编写 `docs/database/migrations/support_db/V1__create_support_ticket_tables.sql`。
- [ ] 静态检查字段、索引、唯一键、字符集与账号授权。
- [ ] 执行迁移并核对两张表、索引、账号权限。
- [ ] 重复执行一次，确认脚本幂等且无副作用。
- [ ] 记录实际执行结果与未验证项。

### 阶段 2：服务骨架

- [ ] 根 `pom.xml` 注册 `support-service`。
- [ ] 新建 `support-service/pom.xml`，依赖与 `comment-service` 对齐。
- [ ] 新建 `application.yml`：端口 9108、数据源、Redis、Nacos、Feign 超时、JWT、内部令牌、文件服务地址。
- [ ] 新建启动类与安全配置，提供 `GET /support/ping`。
- [ ] 网关仓库配置增加业务路由与文档路由。
- [ ] 网关 Swagger 聚合增加 `support-service`。
- [ ] 静态检查依赖、包名、配置项与路由前缀。

### 阶段 3：学生接口

- [ ] 实体、Mapper 与注解 SQL：创建、按公开标识查询、我的列表、条件关闭、未读清零。
- [ ] 分类、类型、状态枚举与白名单校验。
- [ ] 创建工单：订单反查、窗口反查、餐厅反查、图片校验、幂等与摘要。
- [ ] 我的列表：游标分页与摘要。
- [ ] 详情：本人校验、时间线过滤、未读清零。
- [ ] 补充说明：状态校验、消息写入、幂等。
- [ ] 关闭工单：条件更新与系统消息。
- [ ] 统一异常文案与 404 策略。

### 阶段 4：管理接口

- [ ] 队列查询与筛选，应用数据范围。
- [ ] 管理详情：含内部备注与 `customerId`。
- [ ] 受理、公开回复、内部备注、关闭。
- [ ] 角色注解与范围判定；`STALL_MANAGER` 一律拒绝，`SYSTEM_ADMIN` 只可见软件工单。
- [ ] 条件更新与重复操作处理。

### 阶段 5：通知

- [ ] notification-service 增加内部控制器与共享密钥校验。
- [ ] Mapper 增加 `SUPPORT_REPLY` 插入方法。
- [ ] 安全配置放行 `/internal/**`，网关不暴露。
- [ ] 事务提交后推送在线提示。
- [ ] support-service 增加通知客户端与失败降级日志。

### 阶段 6：学生端

- [ ] 新增 `api/support.uts`。
- [ ] 新增 `styles/support.css`。
- [ ] 实现提交页、列表页、详情页。
- [ ] `pages.json` 注册三条路由。
- [ ] `mine.uvue` 增加「帮助与支持 / 帮助与反馈」入口。
- [ ] 检查按钮 class、色板、空态、错误态与加载态。
- [ ] 检查图片上传、预览与移除。
- [ ] 检查关闭确认与重复提交保护。

### 阶段 7：验证与交付

- [ ] 后端仅做源码静态检查，不运行 Maven、不构建、不重启服务。
- [ ] 前端运行 `node scripts/test-vapor.cjs`。
- [ ] 确认 `test-order-flow.cjs` 的既有失败与本次无关。
- [ ] 确认 `E:\oreder_sys_cloud_admin` 无任何改动。
- [ ] 汇总未验证项：服务启动、网关路由生效、真实 JWT 联调、通知实时推送需要实际运行验证。

## 9. 验证清单

### 9.1 后端静态检查

- [ ] 所有新增依赖在父工程或子模块中声明完整。
- [ ] Mapper 注解 SQL 的字段名与迁移脚本一致。
- [ ] 所有业务值使用 `#{}` 参数绑定，无字符串拼接。
- [ ] 权限注解覆盖学生与管理接口，默认拒绝未声明路径。
- [ ] 内部接口不在网关路由中。
- [ ] 状态变更全部带状态条件。
- [ ] 幂等键缺失、超长、冲突均有明确处理。
- [ ] 异常不泄露 SQL、堆栈与他人数据。

### 9.2 数据库核验

- [ ] `support_db` 存在且字符集正确。
- [ ] `support_ticket`、`support_ticket_message` 字段、类型、注释完整。
- [ ] 唯一键与索引与计划一致。
- [ ] `support_service` 只能访问 `support_db`。
- [ ] 迁移脚本可重复执行。

### 9.3 前端检查

- [ ] `node scripts/test-vapor.cjs` 通过。
- [ ] 三条路由可被正确注册与返回。
- [ ] 入口仅在 `CUSTOMER` 角色显示。
- [ ] 提交页两种类型切换无残留数据。
- [ ] 图片最多 3 张，超限有提示。
- [ ] 列表分页、空态、失败重试正常。
- [ ] 详情页关闭后只读。
- [ ] 所有按钮文案与样式符合现有规范。

### 9.4 运行验证（需实际启动服务，交付时说明）

- [ ] 缺少令牌返回 401，非顾客角色返回 403。
- [ ] 学生只能看到自己的工单。
- [ ] 窗口工作人员（`STALL_MANAGER`）访问管理接口返回 403，不可查看或处理任何用餐工单。
- [ ] 餐厅经理只能看到本餐厅工单。
- [ ] 总经理可看全部用餐工单与软件工单。
- [ ] 系统管理员只能看软件工单。
- [ ] 商家回复后学生收到站内通知与未读红点。
- [ ] 关闭后不能补充、不能再次回复。

## 10. 风险与待确认

1. **通知中心语义（已确认，见 §12.1）**：反馈回复会进入现有消息中心，但该页当前叫「订单中心」。已确认拆分为独立的「工单通知」入口，工单消息不再混入订单通知。
2. **软件工单处理角色**：默认 `GENERAL_MANAGER` + `SYSTEM_ADMIN`；若只允许总经理，可去掉 `SYSTEM_ADMIN`。
3. **管理端详情是否需要提交人昵称**：首期只返回 `customerId`，不额外调用 user-service；后续接管理端时再补昵称。
4. **订单选择范围**：提交页默认展示最近一页订单；若订单很多，后续可增加按订单号搜索。
5. **通知实时性**：依赖 notification-service 在线推送；推送失败只保留站内记录，不保证即时到达。
6. **工单编号**：`displayNo` 取 UUID 前 8 位，仅用于展示；若后续需要连续编号，再单独设计序列。

## 11. 交付物清单

- 迁移脚本：`docs/database/migrations/support_db/V1__create_support_ticket_tables.sql`
- 后端服务：`support-service` 全部源码与配置
- 网关配置：`docs/nacos/gateway-service.yaml`、`gateway-service/src/main/resources/application.yml`
- 通知改造：`notification-service` 内部通知接口与 Mapper
- 学生端：`api/support.uts`、`pages/support/*.uvue`、`styles/support.css`、`pages.json`、`mine.uvue`
- 验证记录：迁移执行结果、前端测试结果、未验证项说明

## 12. 学生端体验补充（2026-11-23 确认，待实施）

背景：工单通知已接入现有消息中心，但渠道归属与未读语义不完整。以下三项经用户确认纳入实施范围，均不改表结构、不涉及管理端仓库。

### 12.1 工单通知独立入口

目标：工单消息不再进入「订单中心」会话，单独提供「工单通知」入口，只展示工单相关消息。

后端 `notification-service`：

- `GET /notifications` 增加 `channel` 参数：
  - `ORDER`（默认，兼容旧客户端）：`biz_type IS NULL`，只返回订单状态通知；
  - `SUPPORT`：`biz_type = 'SUPPORT'`，只返回工单回复与状态通知。
- `GET /notifications/unread-count` 增加同名 `channel` 参数，分别统计两个渠道；`POST /notifications/read-all` 同样按渠道执行，避免在工单页把订单通知一并标记已读。
- `NotificationMapper` 的分页、未读统计、全部已读 SQL 增加渠道条件；游标子查询继续绑定接收人，禁止跨用户游标。
- 分页与未读查询的 `channel` 只接受白名单值，非法值返回 400；不新增数据库列，沿用 `biz_type` 区分渠道。

前端：

- `api/notifications.uts`：`fetchNotifications(cursor, channel)`、`fetchUnreadNotificationCount(channel)`、`markAllNotificationsRead(channel)`。
- 新增 `pages/notifications/support.uvue`：标题「工单通知」，发送者「帮助与反馈」，固定 `channel=SUPPORT`；点击气泡先标记已读，再跳转 `/pages/support/detail?ticketId=...`；空态、下拉刷新、分页与失败重试沿用 `orders.uvue` 的实现。
- `pages/notifications/orders.uvue` 固定 `channel=ORDER`，不再出现工单消息；发送者保持「订单中心」。
- `pages/notifications/list.uvue` 在「订单通知」入口旁新增「工单通知」入口，两个入口分别展示各自未读数。
- `store/notifications.uts`：新增 `supportUnreadCount`，同时刷新订单与工单两个渠道；底部 Tab 角标 = 订单未读 + 工单未读 + 聊天未读。
- `pages.json` 注册 `pages/notifications/support` 路由。

验收：

- 学生收到回复、受理、关闭通知后，只在「工单通知」出现，订单通知页不再混入。
- 两个入口未读数独立，点击后各自消减，底部角标合计正确。
- 空态、分页、失败重试、账号切换隔离与现有订单通知页一致。

### 12.2 受理与关闭也点亮列表未读

目标：学生不打开消息中心也能在「我的反馈」看到状态变化。

- 管理端受理：`markAccepted` 同时置 `customer_unread = 1`。
- 管理端关闭：条件关闭同时置 `customer_unread = 1`（原实现置 0）；学生本人关闭仍置 0，不给自己制造未读。
- 学生补充说明不改变未读数；学生打开详情即清零，沿用现有逻辑。
- 「我的反馈」列表红点文案由「有新回复」改为「有更新」，覆盖回复、受理、关闭三类。
- 工单列表未读与通知渠道未读相互独立：前者表示工单有新动态，后者表示消息中心有未读记录，互不替代。

验收：

- 管理端受理后，学生列表卡片出现「有更新」，打开详情后消失。
- 管理端关闭后同样出现「有更新」，打开详情后消失。
- 学生自己关闭不产生未读；回复仍正常产生未读。

### 12.3 提交页拦截系统返回

目标：提交请求进行中，系统返回键不能把页面带走，避免成功后的自动跳转落空。

- `pages/support/submit.uvue` 增加 `onBackPress`：`submitting || imageBusy` 时返回 `true` 拦截，否则返回 `false` 放行。
- 页面可见返回按钮的现有守卫保留；拦截只影响系统返回，不改变 Android 之外的其余导航。
- 请求本身的防重复仍由幂等键保证，不依赖页面是否存活。

验收：

- 提交中按系统返回键页面不退出，提交按钮保持禁用。
- 提交失败或完成后返回键恢复正常。

### 12.4 验证与边界

- 后端仅做源码静态检查，不运行 Maven、不构建、不重启；`channel` 参数的 SQL 条件需人工核对。
- 前端运行 `node scripts/test-vapor.cjs`，并检查新增路由与两个通知入口的未读数展示。
- 不改 `notifications` 表结构；不改管理端仓库；不改动既有订单通知的文案与跳转。
- 运行验证项：两个渠道筛选、未读独立计数、Tab 角标合计、工单通知点击跳转、状态未读清零。
