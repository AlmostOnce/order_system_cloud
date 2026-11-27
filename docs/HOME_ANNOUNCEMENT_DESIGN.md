# 首页公告设计说明

> 文档性质：设计讨论稿，不包含实现代码与数据库变更。
> 目标：回答首页公告的五个问题——归属服务、首页位置、呈现方式、发布主体、有效期语义。
> 结论优先：文中的"建议方案"是推荐落地口径；"备选"用于说明为何不选。

## 1. 结论摘要

| 问题           | 建议结论                                                                                                                                       |
|----------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| 归类到哪个服务 | `product-service`（product_db），新增轻量公告能力；不复用 `notification-service`，也不新建独立服务                                              |
| 放在首页哪里   | 学生端首页品牌区（搜索框）下方、"今天想吃什么"推荐区上方，一条通栏公告条；无公告时整块不渲染                                                   |
| 用什么方式呈现 | 单行"喇叭图标 + 公告文案（单行省略）+ 箭头"的窄条，点击打开底部弹层看全文与发布时间；不做自动跑马灯                                            |
| 由谁发布       | Vue 管理端新增"公告管理"页面，仅 `GENERAL_MANAGER`、`SYSTEM_ADMIN` 可发布/下线全平台公告；`RESTAURANT_MANAGER` 不参与首页公告                    |
| 有效期         | **推荐"起止时间 + 结束时间可空"**：结束时间为空表示长期有效，直到手动下线或被下一条公告取代；不建议只依赖"下一条覆盖"                          |

## 2. 现状核对（设计依据）

| 现状                                                                                       | 位置                                                                                                   | 对设计的影响                                                                        |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `restaurants.notice` 已存在（VARCHAR(255)，管理端可编辑"公告 / 简介"）                       | `product_db.restaurants`；`RestaurantDO`、`RestaurantSaveRequest`、`RestaurantsView.vue`               | 它是**餐厅静态简介**，无发布状态、无起止时间，且未进入学生端接口，不能当首页公告用      |
| 学生端餐厅列表只返回 `restaurantId/restaurantCode/restaurantName/location`                  | `RestaurantSummaryVO`、`GET /products/restaurants`                                                     | 即使填了 `restaurants.notice`，学生端目前也拿不到                                      |
| 首页结构：品牌区（餐厅筛选 + 搜索入口）→ 推荐区"今天想吃什么" → 档口区 → 页脚              | `order_system_cloud_user/pages/home/home.uvue`                                                         | 公告条最自然的位置在品牌区与推荐区之间                                              |
| 首页下拉刷新由 `refreshPage()` 分别并发调用 `loadRecommendations()` 与 `loadCatalog()`      | `home.uvue`                                                                                            | 公告应作为第三条独立加载链，失败不阻塞推荐与档口                                    |
| `notifications` 表以 `recipient_id` 为必填，唯一键 `(recipient_id, event_id)`                | `notification_db`、`NotificationCategory`                                                              | 它是"给某个人"的站内通知，广播公告需要为每个学生各写一条或改表语义，写放大且破坏未读语义 |
| `support-service` 存的是问题反馈/工单                                                      | `docs/database/migrations/support_db`                                                                  | 与公告语义无关                                                                       |
| 管理端已有角色矩阵：`STALL_MANAGER / RESTAURANT_MANAGER / GENERAL_MANAGER / SYSTEM_ADMIN`    | `oreder_sys_cloud_admin/src/admin/permissions.ts`、`router/index.ts`                                   | 新页面沿用同一角色矩阵与路由守卫即可                                                |
| 网关 `/api/products/**` 已路由到 product-service                                           | `gateway-service/README.md`                                                                            | 公告接口无需新增网关路由                                                            |

## 3. 归属服务

### 3.1 对比

| 方案                                        | 优点                                                                 | 问题                                                                                                     | 结论   |
|---------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|--------|
| **product-service（推荐）**                 | 已有餐厅/档口目录、管理端写接口、`product_db`、`/api/products/**` 网关路由；公告属于食堂运营内容 | 需要新增一张表和一组接口                                                                                 | 采用   |
| notification-service                        | 名字像"消息"                                                         | 现有模型是"一对一收件箱"（`recipient_id` 必填、按人记已读）；公告是"一份内容给所有人看"，没有已读概念，硬塞会写放大或破坏表语义 | 不采用 |
| support-service                             | 已有管理端处理流程                                                   | 定位是问题反馈与工单，不是内容发布                                                                       | 不采用 |
| 新建 content-service / 独立公告库           | 职责最"纯"                                                           | 为一条公告新增服务、库、账号、依赖、部署与网关配置，成本远大于收益                                       | 不采用 |

### 3.2 建议口径

在 `product-service` 内新增 `home_announcements` 能力（表 + 面向学生的只读接口 + 面向管理端的发布接口），
沿用现有 `ProductSecurityConfig`、`@PreAuthorize`、`ApiResponse` 与网关前缀。

`restaurants.notice` **保持原义**，继续作为餐厅简介由餐厅经理维护；本次不把它挪作首页公告，也不在该字段上叠加
起止时间（一个字段同时承担"简介"和"限时公告"会让语义混乱，且 255 字长度也不适合公告正文）。

## 4. 放在首页哪里

### 4.1 位置

```text
┌──────────────────────────────────────────┐
│ 状态栏占位                                │
│ 校园食堂            [全部餐厅 ⌄]          │  ← hero 品牌区
│ [🔍 搜索菜品、档口或餐厅              ]   │
├──────────────────────────────────────────┤
│ 📢 公告：本周五午餐时段二楼暂停营业…   ›  │  ← 新增：首页公告条（仅 CUSTOMER 且有待展示公告）
├──────────────────────────────────────────┤
│ 今天想吃什么                 为你推荐     │
│ [推荐卡片横向滚动]                        │
├──────────────────────────────────────────┤
│ 全部档口                    3 个在营业    │
│ [档口卡片列表]                            │
└──────────────────────────────────────────┘
```

理由：

- 位于首屏、搜索框正下方，学生进入首页即可看到，且不会挤掉"今天想吃什么"与档口列表的信息密度。
- 放在品牌区内部会与"校园食堂 + 餐厅筛选"争夺视觉层级；放在推荐区之后则容易被忽略。
- 公告是跨餐厅的全平台信息，固定在页面级，不随首页"餐厅筛选"变化而切换，避免"选了餐厅就看不到公告"。

### 4.2 与餐厅筛选的关系

- 建议 v1 只做**全平台公告**，与餐厅筛选无关。
- 若后续需要"某餐厅停业通知"，建议作为 P2 扩展：公告表增加可空 `restaurant_id`，首页按"当前选中餐厅 + 全平台"合并展示；
  在未确定规则前不预留复杂逻辑。

## 5. 用什么方式呈现

### 5.1 建议形态：单行公告条 + 底部弹层

- 默认态：一行高约 44px 的浅色卡片，左侧"公告"小徽标（或喇叭图形），中间单行省略文案，右侧 `›`。
- 点击：打开底部弹层（复用首页现有"选择餐厅"面板的遮罩 + 上滑交互），展示标题、完整正文、发布时间。
- 不做自动跑马灯：`uvue` 下横向滚动动画实现成本高、长时间移动的文字可读性差，也容易与下拉刷新手势冲突。
- 不做多条轮播：v1 只展示"当前生效的最新一条"，避免引入轮播状态与分页。

### 5.2 状态处理

| 场景                     | 表现                                                                 |
|--------------------------|----------------------------------------------------------------------|
| 无生效公告               | 整块不渲染（不占位、不显示"暂无公告"）                               |
| 公告接口失败/超时        | 整块隐藏，不弹错误；不影响推荐区与档口区；下拉刷新时重试             |
| 文案过长                 | 列表态单行省略，弹层内完整展示（正文上限由后端校验）                 |
| 学生切换账号             | 丢弃旧账号的迟到响应（沿用首页 `owner + version` 做法），不串号展示   |
| 非 CUSTOMER 角色         | 不渲染公告条（与首页其他学生功能一致）                               |

## 6. 由谁发布

### 6.1 发布入口

- 入口放在 Vue 管理端（`oreder_sys_cloud_admin`），**不能放进 uni-app**：
  按项目约定，uni-app 只承载学生（CUSTOMER）功能，非学生角色的操作统一进管理端。
- 新增页面 `AnnouncementsView.vue`，路由 `/announcements`，侧栏菜单"公告管理"。
- 页面能力：列表（状态、标题、生效时间、发布人、更新时间）、新建/编辑、发布、下线。

### 6.2 角色与权限

| 角色                 | 首页公告权限 | 说明                                                       |
|----------------------|--------------|------------------------------------------------------------|
| `SYSTEM_ADMIN`       | 发布/编辑/下线 | 平台级内容最终责任人                                       |
| `GENERAL_MANAGER`    | 发布/编辑/下线 | 食堂运营负责人，与现有餐厅管理权限同级                     |
| `RESTAURANT_MANAGER` | 无           | 继续维护 `restaurants.notice`（餐厅简介），不发布首页公告   |
| `STALL_MANAGER`      | 无           | 档口运营，不涉及全平台公告                                 |

- 后端：公告管理 Controller 使用 `@PreAuthorize("hasAnyRole('GENERAL_MANAGER','SYSTEM_ADMIN')")`。
- 前端：`permissions.ts` 增加 `ANNOUNCEMENT_ALLOWED_ROLES` 与导航项，路由 `meta.allowedRoles` 同步；
  前端隐藏只是体验，服务端必须再次校验。
- 公告是平台级数据，不做餐厅数据范围过滤；发布人记录为 `created_by/updated_by`，仅审计展示。

## 7. 有效期语义（时间限制 vs 下一条覆盖）

### 7.1 三种可选策略

| 策略                              | 行为                                                             | 风险                                                                 |
|-----------------------------------|------------------------------------------------------------------|----------------------------------------------------------------------|
| A. 仅"最新一条覆盖"               | 没有时间字段，永远展示最新发布的那条                             | 过期信息会长期挂在首页；必须记得手动下线，否则"上周停业通知"一直在  |
| B. 仅起止时间                     | 必须填结束时间，到点自动消失                                     | 长期公告（如"食堂营业时间"）被迫反复续期，运营成本高                |
| **C. 起止时间 + 结束时间可空（推荐）** | 开始时间必填；结束时间可空。空表示长期有效，直到手动下线或被下一条取代 | 需要提供"下线"操作与清晰的状态展示                                   |

### 7.2 推荐规则

1. 公告状态：`DRAFT`（草稿）→ `PUBLISHED`（已发布）→ `OFFLINE`（已下线）。
2. 生效判定全部在服务端完成：
   `status = PUBLISHED AND start_at <= NOW() AND (end_at IS NULL OR end_at > NOW())`。
3. 多条同时生效时，按 `published_at DESC` 取最新一条返回；v1 首页只展示一条。
4. 结束时间为空 = 长期有效；运营需要它消失时点"下线"，或发布下一条（旧公告仍保留在列表，只是不再展示）。
5. 到期不需要定时任务：展示条件在查询里判断，过期后自然查不到；缓存按短 TTL 失效即可。
6. 时间统一按服务端时区（Asia/Shanghai）解释，客户端不使用本机时间判断是否过期。

一句话回答原问题：**建议给公告设起止时间，但结束时间允许为空；不填就是"一直显示，直到下一条更新或手动下线"。**
只靠"下一条覆盖"不可取，因为运营很容易忘记下线过期内容。

## 8. 接口与数据表建议

### 8.1 数据表（product_db）

```sql
-- 首页公告：平台级内容，与餐厅简介 restaurants.notice 相互独立。
CREATE TABLE home_announcements (
    announcement_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '公告内部编号',
    title           VARCHAR(60)  NOT NULL                COMMENT '公告标题，列表与弹层展示',
    content         VARCHAR(500) NOT NULL                COMMENT '公告正文，纯文本，不做富文本',
    status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0草稿 1已发布 2已下线',
    start_at        DATETIME     NOT NULL                COMMENT '生效开始时间（服务端时区）',
    end_at          DATETIME     NULL                    COMMENT '生效结束时间，NULL 表示长期有效',
    published_at    DATETIME     NULL                    COMMENT '最近一次发布时间，用于多条生效时取最新',
    created_by      BIGINT       NOT NULL                COMMENT '创建人用户编号，仅审计',
    updated_by      BIGINT       NOT NULL                COMMENT '最后修改人用户编号，仅审计',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (announcement_id),
    KEY idx_home_announcements_active (status, published_at),
    KEY idx_home_announcements_window (status, start_at, end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '首页公告';
```

说明：

- 正文限 500 字，纯文本；不支持图片与富文本，避免引入文件引用清理和 XSS 处理。
- `end_at` 可空是"长期有效"的落点；不使用 `status` 之外的软删除字段，历史公告保留可追溯。
- 索引服务于"按状态过滤 + 取最新"和"起止时间判定"两类查询，不为低频管理端列表额外建索引。

### 8.2 学生端接口

```text
GET /api/products/announcements/home
权限：hasRole('CUSTOMER')
响应：data = { announcementId, title, content, publishedAt } 或 data = null
```

- 服务端直接返回"当前应展示的那一条"，客户端不做筛选与排序。
- 空结果返回 `null` 而不是空数组，语义明确。
- 可选缓存：Redis 短 TTL（如 60 秒）+ 发布/下线时主动删除；v1 也可先不缓存（单行索引查询，成本很低），
  若加缓存必须在发布/下线后失效，避免"发布了但首页看不到"。

### 8.3 管理端接口

```text
GET    /api/products/admin/announcements              列表（含草稿/已下线，按更新时间倒序）
POST   /api/products/admin/announcements              新建（草稿或直接发布）
PUT    /api/products/admin/announcements/{id}         编辑（仅草稿/已下线可改，或允许改未生效公告）
POST   /api/products/admin/announcements/{id}/publish 发布，写入 published_at，校验 start_at/end_at
POST   /api/products/admin/announcements/{id}/offline 下线，立即从首页消失
权限：hasAnyRole('GENERAL_MANAGER','SYSTEM_ADMIN')
```

校验规则：

- 标题必填、正文必填，长度按表字段限制；去掉首尾空白后不能为空。
- `end_at` 为空或必须晚于 `start_at`；`start_at` 允许设为将来时间（定时生效）。
- 已发布且正在生效的公告再次发布/编辑时，以"下线后重新发布"或"允许原地更新并刷新 published_at"两者择一，
  实施前确认；建议允许原地更新，但要在列表明确显示"生效中"。

### 8.4 学生端接入点

- `api/product.uts` 增加 `fetchHomeAnnouncement()`（与餐厅/档口同属商品域，不新建 API 模块）。
- `home.uvue` 增加 `announcement/announcementLoading/announcementError` 状态与 `loadAnnouncement()`；
  `refreshPage()` 中与推荐、目录并列发起，失败只隐藏公告条。
- 公告条放在 `hero` 之后、推荐 `section` 之前；弹层组件复用现有底部面板样式与遮罩交互。

## 9. 失败与边界行为

| 情况                                   | 期望行为                                                                 |
|----------------------------------------|--------------------------------------------------------------------------|
| 公告查询失败/超时                      | 隐藏公告条，不提示错误，不影响推荐与档口；下次下拉刷新重试                 |
| 发布/下线成功但缓存未失效              | 发布接口内主动删缓存；缓存 TTL 设短作为兜底                               |
| 公告在页面停留期间到期                 | 本次打开期间不强制消失（无倒计时）；下次刷新/重新进入首页不再返回         |
| 多条公告同时生效                       | 只返回 `published_at` 最新一条；其余保留，不报错                          |
| 公告正文为空或超长                     | 后端拒绝并返回明确错误，不写入数据库                                     |
| 学生切换账号/退出后迟到响应            | 丢弃非当前会话结果，禁止把上一个账号的公告渲染给新账号                    |
| 管理端误操作下线                       | 下线为可逆状态，可重新发布；不物理删除记录                               |

## 10. 实施顺序（仅建议，不含工期）

1. `product_db` 迁移：新增 `home_announcements` 表。
2. `product-service`：公告 Mapper/Service/Controller，学生只读接口 + 管理端接口，补充 `@PreAuthorize` 与中文注释。
3. 管理端：新增"公告管理"页面、路由、权限矩阵与 API 封装。
4. 学生端：首页公告条与底部弹层，接入下拉刷新与账号隔离。
5. 联调验收：无公告不占位、发布即展示、到期自动消失、下线立即消失、接口失败不影响首页其他内容。

## 11. 待确认问题

1. 首页公告是否需要区分餐厅（还是只做全平台）——本文建议先只做全平台。
2. 公告是否允许带图片/跳转链接——本文建议 v1 纯文本，避免引入文件与链接白名单问题。
3. 已生效公告的编辑规则——建议允许原地编辑并刷新发布时间，实施前与运营确认。

## 12. 实施进度（2026-11-26）

已按本文推荐方案落地源码：

- 数据库：新增 `docs/database/migrations/product_db/V15__create_home_announcements.sql`；
  已在本地 `product_db`（127.0.0.1:9095）执行，并通过 `SHOW CREATE TABLE` 核对表、索引与 CHECK 约束。
- 后端 `product-service`：新增 `HomeAnnouncementDO`、`HomeAnnouncementMapper`、
  `HomeAnnouncementSaveRequest`、`HomeAnnouncementVO`、`HomeAnnouncementAdminVO`、
  `HomeAnnouncementService`、`HomeAnnouncementServiceImpl`、
  `HomeAnnouncementController`（学生端 `GET /products/announcements/home`）与
  `HomeAnnouncementAdministrationController`（管理端增改、发布、下线）。
- 管理端：新增 `src/views/AnnouncementsView.vue`、`src/api/announcements.ts`，并在
  `permissions.ts`、`router/index.ts` 增加"公告管理"页面，仅 `GENERAL_MANAGER`、`SYSTEM_ADMIN` 可见。
- 学生端：`api/product.uts` 新增 `fetchHomeAnnouncement`；`pages/home/home.uvue` 增加公告条、
  公告详情底部弹层、独立加载链与账号/会话隔离校验。

本版实现口径：

- v1 不做 Redis 缓存，单行索引查询，发布/下线即时生效。
- 新建公告固定为草稿，发布是独立动作；已发布公告被编辑后刷新 `published_at`。
- 是否展示完全由服务端按 `status + start_at + end_at` 判断，客户端不使用本机时间判断过期。

尚未验证（按约定未运行 Maven 与构建）：

- 未运行 Java 测试、未启动 product-service，因此接口、事务和 SQL 仅做静态检查；
- 未构建或运行 Vue 管理端与学生端，未做浏览器/真机联调；
- 发布→学生端首页展示、到期消失、下线即时消失等运行验收待服务启动后执行。

