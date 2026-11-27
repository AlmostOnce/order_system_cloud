# 评价自由评论区设计文档

> 状态：**后端源码已实施，学生端评论区已接入；开发库迁移已执行并核验，
> 未运行 Maven、未构建、未重启服务**。
> 实施范围：comment-service 评论能力与评价 VO 扩展、order-service 消费事实内部接口、
> notification-service 通知类型与偏好分类，以及学生端 uni-app 的评价卡片入口、评论页与通知跳转。
> 商家回复与屏蔽入口属于 Vue 管理端（另仓），尚未接入。
>
> 目标：把评价区从"商家单方回复"扩展为**自由评论区**——任何登录用户都可以对菜品评价、店铺评价发表评论，
> 也可以回复其他人的评论；商家以官方身份参与，学生之间可以正常讨论、追问、追评。
> 涉及 comment_db 新表、comment-service 接口与 VO 扩展、notification-service 新通知类型。
>
> 历史说明：本文原名《评价回复设计文档》，旧稿只设计"每条评价最多一条商家回复"。
> 按 2026-11-25 的确认，改为自由评论模型；旧稿中的单条回复唯一键、编辑撤回重发等约束已作废，
> 下面所有内容以本稿为准。如需回溯旧方案，可查看 Git 历史。
>
> 阅读顺序建议：第 1 节（结论与待决策）→ 第 5 节（库表）→ 第 6 节（接口）→ 第 7 节（权限）→ 第 10 节（防刷与并发）。

---

## 1. 结论摘要与待决策项

**建议方案（下文按此详细展开）**

- 新增独立表 `review_discussion`，一条评价对应**任意多条**评论，不为两张评价表各加列。
- 评论分两层：顶层评论（楼层）+ 楼中楼回复。回复统一挂到楼层根节点，最深两层，不做无限嵌套。
- 评论主体是所有登录用户：学生可以评论任何可见评价，商家角色以官方身份参与；不接收客户端自报身份。
- 评论准入要求"消费过"：菜品评价需有该菜品的已完成订单，店铺评价需有该店铺的已完成订单；管理角色豁免。
  已完成的订单是终态且不可再退款，因此"已完成"即净消费，天然排除下单后退款再评论的刷法。
- 评论只写 comment_db 单库单事务，不碰 MongoDB；不涉及分布式事务，也不影响评分汇总与评分 MQ。
- 评价列表只返回评论数量与前 3 条预览，完整评论走独立分页接口，避免列表响应膨胀与 N+1。
- 评论发布后通过 notification-service 通知"被评论内容的相关人"，使用新的通知类型与新的偏好分类。
- 防刷采用三层：Redis 固定窗口限流（用户维度、用户+评价维度、评价维度）+ 分布式锁防连点 + `request_key` 唯一键兜底。
- 学生端（uni-app）承载学生评论与浏览；商家的回复、屏蔽入口属于 Vue 管理端，遵守用户端/管理端边界。

**需要你拍板的 9 个点**

| # | 决策点 | 建议默认 | 备选 |
| --- | --- | --- | --- |
| D1 | 评论层级 | 两层：顶层评论 + 楼中楼回复 | 无限嵌套（实现与展示成本显著上升） |
| D2 | 谁能评论 | 所有登录用户；学生为主，商家角色以官方身份参与 | 仅学生可评论，商家只能看 |
| D3 | 每条评价评论条数 | 不限条数 | 每人每评价限 1 条（更接近"追评"） |
| D4 | 作者展示名 | 学生昵称 / 商家档口名，均用服务端快照 | 学生统一匿名展示 |
| D5 | 列表集成方式 | 评价 VO 增加评论数与前 3 条预览 | 只给数量，进入详情再拉 |
| D6 | 评论通知分类 | 新增独立分类 `REVIEW`（可单独关闭） | 并入 `SUPPORT` 分类 |
| D7 | 管理员屏蔽 | 支持（档口范围内可屏蔽，学生不可屏蔽他人） | 本期不做屏蔽，只做作者撤回 |
| D8 | 评论是否带图 | 不支持，纯文本 1 至 500 字 | 支持 1 至 3 张图（复用 COMMENT_IMAGE） |
| D9 | 评论准入门槛 | 必须消费过：菜品评价看该菜品完成订单，店铺评价看该店铺完成订单；管理角色豁免 | 不校验消费，只靠限流与屏蔽 |

D1、D2、D9 定错会导致表结构、权限矩阵与跨服务接口返工，建议先确认这三项。

## 2. 需求范围

**做**

1. 登录用户对单条菜品评价（`comments`）发表顶层评论。
2. 登录用户对单条店铺评价（`window_reviews`）发表顶层评论。
3. 登录用户回复楼层内的某条评论（楼中楼），可连续对话，但层级固定为两层。
4. 评论作者编辑、撤回自己的评论（软删除，撤回后不再展示）。
5. 具备档口管理范围的商家角色可以屏蔽（隐藏）本档口评价下的违规评论。
6. 评价列表/详情返回评论数量与前 3 条预览；独立接口分页读取完整评论与楼层回复。
7. 评论/回复发布时给相关人发站内信（评价作者、被回复人），并按偏好分类过滤。
8. 提交侧的防刷、幂等与并发保护。
9. 评论准入校验：未消费该菜品/该店铺的用户不能发表评论；管理角色豁免。

**不做（本期明确排除）**

- 点赞、点踩、表情回应、@ 某人、富文本：本期只做纯文本评论。
- 无限层级嵌套（逐层缩进）：回复条数不限，但所有回复都挂在同一楼层根下，通过 `parent_id` 表达"回复谁"，
  不再增加缩进层级。这与"不能一直回复"是两件事，见 4.3。
- 评论带图片：如确有需要，见 D8，复用既有 `COMMENT_IMAGE` 上传链路，表加 `images` JSON 列。
- 学生举报入口与人工审核工作台：本期只提供管理员屏蔽能力，举报与审核队列另立。
- 评论影响评分、排序、推荐权重：**评论不参与任何评分计算**，也不进入评分 outbox。
- 评论全文检索、热评排序：只按时间倒序/正序，不引入热度算法。
- uni-app 新增商家入口：商家的回复与屏蔽操作在 Vue 管理端。
- 复用聊天（chat）或工单（support）承载评价讨论：语义不同，见 4.4。

## 3. 现状盘点（设计依据）

| 项 | 现状 |
| --- | --- |
| 菜品评价 | MySQL `comments`（元数据、评分、状态 1/2、`has_images`）+ MongoDB `comments`（正文、图片、标签） |
| 店铺评价 | MySQL `window_reviews`（正文、图片、标签、评分、状态，单库单表） |
| 评价唯一性 | `comments` 有 `uk_comment_purchase(order_id, product_id)`；`window_reviews` 有 `uk_window_review_order(order_id)` |
| 评价公开标识 | `comments.comment_id`、`window_reviews.review_id`，均为 CHAR(36) |
| 评价编辑/删除 | 已实现（`PUT/DELETE /comments/{commentId}`、`/comments/window-reviews/{reviewId}`），角色门禁 + 数据范围双重校验 |
| 评价权限 | `ReviewAuthorityService`：作者本人、系统管理员、总经理放行；餐厅经理、档口负责人按数据范围（档口到餐厅）判定；提供 `coversWindow(operatorId, windowId)` |
| 购买事实校验 | `PurchaseVerifier` 只支持按 `orderId` 校验：订单归属本人、状态为"已完成"、明细含该菜品；**没有"按用户+菜品/店铺"的查询接口** |
| 订单完成语义 | `OrderStatus.COMPLETED` 是终态，不可再流转（含退款）；退款只发生在未完成订单，因此"已完成"即净消费 |
| 评价列表 | 游标分页，`limit` 1 至 50，支持 `score`（1 或 5）与 `hasImages` 筛选；列表按页批量解析作者头像 |
| 评论能力 | **当前完全没有**（无评论表、无评论接口、无评论 VO） |
| 通知 | `notifications` 表 + `SystemNotificationWriter` 统一写入（校验、截断、幂等、偏好、提交后 WebSocket 提示） |
| 通知分类 | `NotificationCategory`：ORDER / SUPPORT / COUPON / CHAT |
| 公共 Redis 能力 | `RedisOperationsService.increment(key, Duration)` 为原子自增且只在首次设置 TTL；`DistributedLockService.tryAcquire(...)` 提供 Redisson 锁；**没有现成限流器组件** |
| 学生端 | `pages/review/list.uvue`（按 productId 或 windowId 展示）、`pages/review/create.uvue` |
| 管理端 | 独立的 Vue 管理端（不在本仓库），复用同一套 JWT 与角色 |

**关键限制**

1. 两类评价的正文分别落在 MongoDB 与 MySQL，任何"给评价表加评论列"的方案都要写两遍，且菜品评价还要跨库。
2. 评价列表页是分页高频接口，评论数量的读取必须能批量，不能逐条查。
3. `ReviewAuthorityService.requireManageable` 含"评价作者本人"分支，**不适用于评论发布**（学生不是评价作者）；
   发布评论做"评价可见性 + 登录态 + 消费准入"校验，屏蔽评论才复用 `coversWindow` 的范围判定。
4. 公共模块没有限流器，若要做限流必须基于 `RedisOperationsService` 自建固定窗口计数，不能假设已有组件。
5. `DistributedLockService.tryAcquire` 返回可关闭的 `LockHandle`，必须在 `try-with-resources` 中使用，避免锁泄漏。
6. 现有 `PurchaseVerifier` 按 `orderId` 校验，而评论请求没有订单号。要落实"消费过才能评论"，
   必须由 order-service 新增按用户维度的消费事实查询；让前端先选一笔订单再评论不可接受（多笔订单难选，评论他人评价时更别扭）。

## 4. 方案选型

### 4.1 独立表 vs 评价表加列

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. `comments.reply_content` + `window_reviews.reply_content` | 查询零额外表 | 两处实现、菜品评价跨 Mongo/MySQL、只能存一条、无法扩展多用户讨论 | 否 |
| B. 独立表 `review_discussion`，`review_type + review_id` 指向评价 | 一套逻辑覆盖两类评价；天然多行多用户；评价表零改动 | 列表需批量查询（一次 IN 查询可解决） | **采用** |

选择 B 的额外理由：评价表的软删除、评分汇总、图片投影等既有语义完全不受影响，迁移风险最低；
评论是"高频追加写、按评价聚合读"的数据，独立表的索引可以只为评论场景设计。

### 4.2 单条回复 vs 自由评论

旧稿按"每条评价一条商家回复"设计，用 `uk_review_reply_target(review_type, review_id)` 做唯一约束。
自由评论模型下：

- 同一评价可以被多个学生评论，也可以被商家评论，唯一键会直接阻断正常业务，**必须去掉**。
- 评论需要楼层与回复关系，新增 `root_id`、`parent_id`、`depth` 三列。
- 编辑、撤回从"商家维护官方回复"变成"作者维护自己的发言"，权限判定从"档口范围"改为"作者本人"。

改造成本集中在表结构，接口与 VO 均为新增，不破坏既有评价接口。

### 4.3 两层 vs 无限嵌套

先明确一点：**固定两层不等于"只能回复一次"**。两层限制的是缩进层级，不是回复条数。

- 顶层评论（`depth = 1`）构成楼层，按时间倒序分页。
- 楼中楼回复（`depth = 2`）挂在楼层根下，条数不限，按时间正序分页。
- C 回复 B 时，B 是楼内回复而不是楼层根，此时仍写 `depth = 2`、`root_id` 取楼层根、`parent_id` 记 B，
  前端展示"C 回复 B：……"，视觉上仍在同一列。D 回复 C、E 回复 D 依此类推，
  **对话可以无限继续，只是不再逐层缩进**。

无限嵌套与之的差别只在"是否缩进"：

```
A：这家菜太咸
  └ B：我觉得还好
      └ C：你可能口味重
          └ D：楼上说得对
```

真正的无限嵌套要把每个节点建成独立子树，代价是：

1. 分页失效：树没法按页切，一页 20 条可能只是某棵子树，必须按楼层分页后再递归加载子树。
2. 移动端放不下：每层缩进后正文可用宽度快速衰减，五层以后基本无法阅读。
3. 治理语义复杂：屏蔽中间节点时，子树是整体隐藏还是提升层级，没有无争议的默认值。
4. 计数与通知复杂：楼层回复数要递归统计，通知链路也要按树回溯。
5. 查询成本高：需要递归 CTE 或应用层建树，游标分页几乎不可用。

因此采用两层：需要"回复谁"的语义用 `parent_id` 表达，需要"整层对话"用 `root_id` 一次查出，
需要"往上追溯对话链"时只在单条详情或通知里按 `parent_id` 回溯，列表接口不做递归。
若将来确实需要无限缩进，再改为递归查询或物化路径，属于独立方案。

### 4.4 为什么不复用 chat / support

- 商家聊天（chat）是"学生与商家的私密会话"，评价评论是"公开内容"，可见性与通知对象都不同。
- 工单（support）是"问题处理流程"，有状态机与处理时限，评论没有。
- 复用的结果是公开内容混进私密表、或评论被工单状态机卡住，二者都不合适。

### 4.5 消费准入放在写路径，不放在读路径

"必须消费过才能评论"只约束**发表**（顶层评论与楼中楼回复），不约束读取：

- 未消费的学生仍然可以浏览评价与评论，否则评价区失去参考价值。
- 读路径是高频接口，逐条校验购买事实会引入不必要的跨服务调用。
- 写路径本身已有限流（每用户每分钟 10 条），再叠加一次带缓存的准入查询，压力可控。

准入判定依赖 order-service 的消费事实，不在 comment-service 本地保存订单副本：

- 订单状态变化（完成、退款）由 order-service 权威维护，评论服务抄一份就要面对同步与补偿问题。
- 本期不引入"消费资格投影表"，避免为评论再建一条 MQ 链路与对账任务。
- 校验结果做短缓存，兼顾性能与"刚完成订单即可评论"的时效（见 10.6）。

## 5. 数据模型

### 5.1 新表 `review_discussion`

```sql
CREATE TABLE IF NOT EXISTS review_discussion
(
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键，不对外暴露，用于游标分页',
    discussion_id CHAR(36)      NOT NULL COMMENT '公开评论标识',
    review_type   VARCHAR(16)   NOT NULL COMMENT '所属评价类型：COMMENT 菜品评价，WINDOW_REVIEW 店铺评价',
    review_id     CHAR(36)      NOT NULL COMMENT '所属评价的公开标识，对应 comments.comment_id 或 window_reviews.review_id',
    window_id     BIGINT        NOT NULL COMMENT '评价所属档口编号，冗余用于数据范围判定与按档口检索，不建跨服务外键',
    root_id       CHAR(36)      NOT NULL COMMENT '所属楼层根评论标识；顶层评论的 root_id 等于自身 discussion_id',
    parent_id     CHAR(36)      NULL COMMENT '被直接回复的评论标识；顶层评论为 NULL',
    depth         TINYINT       NOT NULL COMMENT '楼层深度：1 顶层评论，2 楼中楼回复；不开放更深层级',
    author_id     BIGINT        NOT NULL COMMENT '评论人内部用户编号，不对外暴露',
    author_role   VARCHAR(24)   NOT NULL COMMENT '评论时的角色快照：CUSTOMER/STALL_MANAGER/RESTAURANT_MANAGER/GENERAL_MANAGER/SYSTEM_ADMIN',
    author_name   VARCHAR(64)   NOT NULL COMMENT '评论时的展示名快照：学生昵称或商家档口名',
    content       VARCHAR(500)  NOT NULL COMMENT '评论正文，纯文本，前端按文本渲染不解释 HTML',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 已发布，2 作者撤回，3 管理员屏蔽',
    version       INT           NOT NULL DEFAULT 1 COMMENT '内容版本，每次编辑或状态变化递增，用于乐观并发与通知幂等',
    request_key   CHAR(36)      NOT NULL COMMENT '客户端幂等请求 UUID',
    created_at    DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次发布时间',
    updated_at    DATETIME(3)   NULL COMMENT '最近编辑、撤回或屏蔽时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_discussion_public (discussion_id),
    -- 同一作者同一请求键只落一条，吸收网络重试与客户端重复提交。
    UNIQUE KEY uk_review_discussion_request (author_id, request_key),
    -- 评价下的楼层列表：按评价过滤后取 depth=1，再按 id 倒序游标分页。
    KEY idx_review_discussion_review (review_type, review_id, depth, status, id),
    -- 楼层内回复列表：固定 root_id 后按 id 正序游标分页。
    KEY idx_review_discussion_root (root_id, status, id),
    -- 按档口做运营检索与批量计数。
    KEY idx_review_discussion_window (window_id, status, id),
    CONSTRAINT ck_review_discussion_status CHECK (status IN (1, 2, 3)),
    CONSTRAINT ck_review_discussion_depth CHECK (depth IN (1, 2)),
    CONSTRAINT ck_review_discussion_version CHECK (version > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='评价的自由评论区，一条评价对应多条评论';
```

**字段决策说明**

- `review_type + review_id`：不建外键（跨两类目标且评价本身可能软删除），由服务层校验目标存在且可见。
- `window_id` 冗余：屏蔽操作的档口范围判定、按档口检索都不必回查评价表；评价的档口写入后不再变化，冗余不会过期。
- `root_id`：楼层聚合键。顶层评论写入时 `root_id = discussion_id`，回复时复制父评论的 `root_id`，
  这样一次索引查询就能取到整层，不需要递归。
- `parent_id`：只用于展示"回复 @某人"和定位通知接收人，不参与层级计算。
- `depth`：约束为 1 或 2，从库层阻止未来代码写出第三层，避免展示与查询复杂化。
- `author_role`：角色快照。商家角色展示"官方"标识必须用发布时的角色，不能按当前角色回算，
  否则人员调岗后历史评论会突然变成普通学生发言。
- `author_name`：展示名快照。学生改昵称、档口改名后，历史评论保持当时的事实；与评价作者的 `authorName` 口径一致。
- `version`：编辑不重新通知，撤回与屏蔽后 `version` 递增，通知事件号用它区分，避免重试时重复通知。
- 不加 `reply_count`、`like_count`：列表计数用一次 `GROUP BY` 聚合，避免冗余计数与真实状态不一致；
  点赞本期不做，不预留无意义字段。
- `request_key` 由客户端每次提交新生成。撤回后重新发表属于新的一次提交，必须换新键；
  复用旧键会被 `uk_review_discussion_request` 判为重复请求：原记录仍为已发布时返回原评论，
  原记录已撤回或屏蔽时返回 409，提示重新发起。

### 5.2 为什么不再有单条唯一键

旧稿的 `uk_review_reply_target(review_type, review_id)` 必须删除：

- 自由评论的核心就是"一条评价多人、多条发言"，该唯一键会直接拒绝第二条评论。
- 单条唯一键还隐含"只能商家回复"的产品假设，与 D2 冲突。
- 幂等改由 `uk_review_discussion_request(author_id, request_key)` 保证，这是"同一请求只落一次"，与"一条评价多条评论"不冲突。

### 5.3 迁移脚本

`docs/database/migrations/comment_db/V6__create_review_discussion.sql`
（现有最大版本为 `V5__review_edit_and_delete.sql`，因此新脚本编号为 V6）

- 仅 `CREATE TABLE IF NOT EXISTS`，可重复执行。
- 不修改 `comments`、`window_reviews` 任何列，不触碰 MongoDB，不改评分相关表。
- 回滚：先下线评论接口，再 `DROP TABLE review_discussion`；评价数据与评分不受影响。
- 执行记录（2026-11-26）：开发库 9095 恢复监听后已通过 MySQL 容器执行 V6 并核验表、索引与约束；
  脚本只用 `IF NOT EXISTS`，重复执行不会改动已有数据。核验明细见第 13 节。

### 5.4 容量与索引估算

- 行数上界约为"评价总数 × 平均评论数"。校园场景按每评价 0 至 5 条估算，量级为评价数的数倍，可控。
- 评价列表计数：对当前页最多 50 个 `review_id` 做一次 `GROUP BY`，走 `idx_review_discussion_review`。
- 楼层列表：固定 `root_id` 后按 `(status, id)` 范围扫描，走 `idx_review_discussion_root`。
- 按档口运营检索走 `idx_review_discussion_window`，不做全表扫描。
- 本期不加缓存：评论计数直接查库；若真实压测出现瓶颈，再考虑在评价详情做短缓存，不用缓存替代一致性。

## 6. 接口设计

统一前缀 `/comments`，网关路径加 `/api`。全部要求 JWT；读取接口不限制角色，写入接口见第 7 节。

### 6.1 发表顶层评论

```
POST /comments/{commentId}/discussions
POST /comments/window-reviews/{reviewId}/discussions
Header: Idempotency-Key: <UUID>
Body: {"content": "我也吃过，出餐确实快"}
```

- 成功后返回 `ReviewDiscussionVO`，`depth = 1`、`rootId` 等于自身 `discussionId`、`parentId` 为 null。
- 同 `Idempotency-Key` 且原记录仍为已发布、内容一致 → 返回原评论（幂等）；
  同键不同内容、或原记录已撤回/屏蔽 → 409（后者提示重新发起）。
- 评价不存在、已撤回或不可见 → 404，不允许对不可见评价"盲评"。
- 未消费该菜品/该店铺 → 403（管理角色豁免），判定见 7.2。
- 超出限流阈值 → 429（见第 10 节）。

### 6.2 回复评论（楼中楼）

```
POST /comments/discussions/{discussionId}/replies
Header: Idempotency-Key: <UUID>
Body: {"content": "同意，窗口换了新餐盘"}
```

- `{discussionId}` 可以是楼层根评论，也可以是楼层内任意一条回复；服务端统一把 `root_id` 归到楼层根，`depth` 固定为 2。
- 回复条数不限：同一楼层内可以持续对话，C 回复 B、D 回复 C 都写入同一楼层，不增加缩进层级。
- 被回复评论已撤回或已屏蔽 → 409（"该评论已不可回复"），避免把回复挂到不可见内容上。
- 未消费该菜品/该店铺 → 403（管理角色豁免），判定见 7.2。
- 不允许回复跨评价的评论：`review_type + review_id` 以楼层根为准，忽略客户端任何评价参数。

### 6.3 编辑评论

```
PUT /comments/discussions/{discussionId}
Body: {"content": "..."}
```

- 仅作者本人可编辑；管理角色不能改他人评论内容，只能屏蔽。
- 条件更新：`WHERE discussion_id = ? AND author_id = ? AND status = 1 AND version = ?`，未命中返回 409 让客户端刷新。
- 编辑不重新发通知（避免反复改字骚扰被评论人），`version` 递增、`updated_at` 刷新。
- 编辑不改变 `root_id`、`parent_id`、`depth`、`created_at`、排序位置。

### 6.4 撤回评论

```
DELETE /comments/discussions/{discussionId}
```

- 仅作者本人可撤回；软删除：`status = 2`、`version + 1`、`updated_at` 刷新，记录保留用于追责。
- 重复撤回幂等成功（返回 200），不报错。
- 撤回楼层根评论后，该楼层及其回复整体不再展示；撤回楼内回复只隐藏该条。
- 撤回后保留 `content`，不做物理删除，供管理员追溯。
- 已被管理员屏蔽的评论不允许作者自行撤回（`status = 1` 才可撤回），返回 409，避免作者用撤回覆盖屏蔽记录。

### 6.5 读取顶层评论（分页）

```
GET /comments/{commentId}/discussions?limit=20&cursor=...
GET /comments/window-reviews/{reviewId}/discussions?limit=20&cursor=...
```

- 只返回 `depth = 1 AND status = 1` 的楼层，按内部 `id` 倒序（最新在前）。
- `cursor` 是上一页最后一条的公开 `discussionId`，与评价分页保持一致：对外只暴露公开标识，
  服务端内部把 `discussionId` 换算成自增 `id` 后做范围查询。游标不属于当前评价时返回 400。
- `limit` 1 至 50，默认 20。
- 每条楼层附带 `replyCount`（该楼层下可见回复数）与 `replies`（前 3 条预览，按时间正序）。
  `replyCount` 对当前页楼层做一次 `GROUP BY root_id` 批量查询，不逐条查。

### 6.6 读取楼层回复（分页）

```
GET /comments/discussions/{discussionId}/replies?limit=50&cursor=...
```

- `{discussionId}` 可以是楼层根或楼内任意一条，服务端解析出 `root_id` 后统一查询。
- 只返回 `depth = 2 AND status = 1`，按内部 `id` 正序（对话顺序）。
- `cursor` 同样是上一页最后一条的公开 `discussionId`，服务端换算为内部 `id`；不属于该楼层时返回 400。
- `limit` 1 至 100，默认 50；评论场景单层回复通常少于 100 条，不需要更复杂的分页。
- `parentId` 指向的评论可能不在当前页（被回复的是更早的回复）。展示"回复 @某某"所需的作者名，
  对当前页的 `parent_id` 集合做一次批量查询，不逐条回查父评论。

### 6.7 管理员屏蔽评论

```
POST /comments/discussions/{discussionId}/hide
```

- 无请求体：屏蔽原因不落库（表中没有对应列），避免接口接收了字段却不保存造成误解。
- 角色门禁：STALL_MANAGER / RESTAURANT_MANAGER / GENERAL_MANAGER / SYSTEM_ADMIN。
- 数据范围：`ReviewAuthorityService.coversWindow(operatorId, windowId)` 必须为 true；系统管理员与总经理直接放行。
- 置 `status = 3`、`version + 1`、`updated_at` 刷新；重复屏蔽幂等成功。
- 操作留痕：本期只写应用日志（操作人编号、评论标识、原状态、时间），不新建审计表；
  若后续要求可查询的处罚记录，再加 `review_discussion_moderation` 表。
- 学生不能屏蔽他人评论，只能撤回自己的评论。

### 6.8 响应体 `ReviewDiscussionVO`

```json
{
  "discussionId": "6b2f...",
  "reviewType": "COMMENT",
  "reviewId": "a1111111-1111-4111-8111-111111111111",
  "rootId": "6b2f...",
  "parentId": null,
  "depth": 1,
  "authorName": "李同学",
  "authorAvatarUrl": "https://...",
  "authorRole": "CUSTOMER",
  "official": false,
  "parentAuthorName": null,
  "content": "我也吃过，出餐确实快",
  "version": 1,
  "createdAt": "2026-11-25T10:12:33",
  "updatedAt": null,
  "replyCount": 2,
  "replies": [],
  "mine": false
}
```

- `official`：由 `author_role` 是否为管理角色派生，前端据此展示"商家"标识；不返回内部用户编号。
- `mine`：当前登录用户是否为作者，前端据此显示编辑/撤回入口。
- `version`：编辑时客户端必须原样回传，版本不一致返回 409。
- `parentAuthorName`：被回复人的展示名；父评论已撤回或屏蔽时返回"已删除的评论"，不泄露原名称。
- `replies` 只在顶层列表中出现，最多 3 条；楼层回复接口返回完整分页，不再嵌套。
- `authorAvatarUrl` 复用 `AuthorAvatarService` 批量解析，不能按行远程调用。

### 6.9 错误码约定

| 场景 | HTTP | 业务码 | 提示 |
| --- | --- | --- | --- |
| 评价不存在或已撤回 | 404 | NOT_FOUND | 评价不存在 |
| 评论不存在 | 404 | NOT_FOUND | 评论不存在 |
| 评论已撤回或被屏蔽 | 409 | CONFLICT | 该评论已不可操作 |
| 无该档口屏蔽权限 | 403 | FORBIDDEN | 没有管理该档口评价的权限 |
| 编辑/撤回他人评论 | 403 | FORBIDDEN | 只能操作自己的评论 |
| 未消费该菜品/该店铺 | 403 | FORBIDDEN | 仅购买过该菜品/该店铺的用户可以评论 |
| 被回复评论已不可回复 | 409 | CONFLICT | 该评论已不可回复 |
| 同请求键不同内容 | 409 | CONFLICT | 请求标识已用于其他评论内容 |
| 编辑时版本已变化 | 409 | CONFLICT | 评论已变更，请刷新后重试 |
| 内容为空或超长 | 400 | BAD_REQUEST | 评论内容需为 1 至 500 字 |
| 游标与当前评价/排序不匹配 | 400 | BAD_REQUEST | 分页游标无效 |
| 触发限流 | 429 | TOO_MANY_REQUESTS | 操作过于频繁，请稍后再试 |
| 数据范围无法确认 | 503 | SERVICE_UNAVAILABLE | 无法确认数据范围，请稍后重试 |

## 7. 权限设计

### 7.1 角色与数据范围

角色门禁放在 Controller（`@PreAuthorize`），数据范围与归属判定放在 Service，二者同时通过才允许操作：

| 操作 | CUSTOMER（学生） | STALL_MANAGER | RESTAURANT_MANAGER | GENERAL_MANAGER | SYSTEM_ADMIN |
| --- | --- | --- | --- | --- | --- |
| 查看评论 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 发表顶层评论 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 回复评论 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 编辑自己的评论 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 撤回自己的评论 | 允许 | 允许 | 允许 | 允许 | 允许 |
| 屏蔽他人评论 | 拒绝 | 仅授权档口 | 仅授权餐厅下档口 | 允许 | 允许 |

实现要点：

- **发表评论不做档口范围校验**。评价本身是公开内容，学生只要能看见就能评论；
  若强制要求"档口范围"，学生会永远无法评论，与自由评论目标冲突。
  校验项：登录态有效、评价存在且 `status = 1`、评价作者未被删除、通过消费准入（见 7.2）。
- **屏蔽必须做范围校验**：复用 `ReviewAuthorityService.coversWindow(operatorId, windowId)`，
  不复用 `requireManageable` 的"评价作者本人"分支。
- **编辑/撤回只认作者本人**：`author_id` 必须等于当前用户编号；管理角色也不能改他人正文，避免"篡改用户发言"。
  若商家需要回应，正常发表自己的评论即可，官方身份由 `author_role` 体现。
- 评论人的展示名与角色来自服务端解析后的 JWT 声明，**不接收客户端自报名称或角色**。
- 学生昵称的取法与评价作者保持一致（现有 `authorName` 生成逻辑），不新增一套口径。
- 学生端/管理端边界：学生评论、浏览、撤回自己的评论属于 CUSTOMER 功能，可进 uni-app；
  商家回复与屏蔽入口属于管理端，不在 uni-app 新增任何入口。

### 7.2 评论准入：消费事实校验

发表评论（顶层与回复）要求操作者**消费过被评论的对象**，按评价类型分别判定：

| 评价类型 | 准入事实 | 判定口径 |
| --- | --- | --- |
| 菜品评价（`COMMENT`） | 买过该菜品 | 存在 `user_id = 操作者`、`status = 已完成`、明细含该 `product_id` 的订单 |
| 店铺评价（`WINDOW_REVIEW`） | 在该店铺消费过 | 存在 `user_id = 操作者`、`status = 已完成`、`window_id = 该店铺` 的订单 |

- **不混用**：买过 A 菜不能评论 B 菜的评价；买过该店铺任一菜品即可评论店铺评价。
- **状态只认"已完成"**：`COMPLETED` 是终态且不能退款，等于净消费；
  已退款（`REFUNDED`）、已取消（`CANCELLED`）以及未取货的订单都不算。
- **管理角色豁免**：STALL_MANAGER / RESTAURANT_MANAGER / GENERAL_MANAGER / SYSTEM_ADMIN 跳过消费校验，
  否则商家无法参与本档口评价的讨论。
- **评价作者本人天然满足**：作者能发评价就说明有完成订单，不做特判。
- 校验只看服务端事实，客户端不能传"我已消费"或订单号来绕过。

**实现方式**

现有 `PurchaseVerifier.requireCompletedPurchase(orderId, productId, bearer)` 按 `orderId` 校验，
评论请求没有订单号，不能直接复用。由 order-service 新增内部接口：

```
GET /internal/orders/purchase-facts?userId=9&targetType=PRODUCT&targetId=3
Header: X-Internal-Token
Response: {"purchased": true}
```

- `targetType` 取 `PRODUCT`（菜品）或 `WINDOW`（店铺），`targetId` 对应菜品编号或档口编号。
- order-service 侧只做 `EXISTS` 查询（完成订单 + 用户 + 商品/档口），不返回订单明细，避免泄露无关数据。
- comment-service 新增 Feign 方法，放在 `CommentOrderClient` 或独立客户端，不复用按订单查询的方法。
- 校验通过后，`window_id` 仍从评价本体读取（评价已冗余档口编号），不依赖该接口返回。

**失败语义**

| 情况 | 返回 | 理由 |
| --- | --- | --- |
| 未消费 | 403 `FORBIDDEN`，"仅购买过该菜品/该店铺的用户可以评论" | 明确告诉用户原因，不用 404 掩盖 |
| order-service 返回业务失败或超时 | 503 `SERVICE_UNAVAILABLE` | 准入规则不能因下游故障放行（fail-closed） |
| Redis 不可用 | 跳过缓存直查 order-service；仍不可用则 503 | 缓存只是加速，不是准入依据 |

这里与限流的降级方向相反：限流失败可以 fail-open（只损失防刷强度），消费准入失败必须 fail-closed，
否则"必须消费过"形同虚设。

## 8. 与评价列表的集成

| 改动点 | 说明 |
| --- | --- |
| `CommentVO` | 新增 `int discussionCount`、`List<ReviewDiscussionVO> discussions`（前 3 条，可空列表） |
| `WindowReviewVO` | 同上 |
| `CommentServiceImpl.page/detail` | 对当前页的评价批量查询评论数量与前 3 条预览后拼装 |
| `WindowReviewServiceImpl.page/forOrder` | 同上 |
| `CommentMapper` / `WindowReviewMapper` | 不改，评论统一由 `ReviewDiscussionMapper` 读取 |
| 前端 | 评价卡片下方展示"查看全部 N 条评论"与最多 3 条预览；无评论不渲染入口 |

内嵌预览是只读摘要：`mine` 恒为 `false`，不显示编辑/撤回按钮。用户点进独立评论接口后，
当前用户身份参与计算，才会返回真实的 `mine`。这样评价列表不必为每条预览解析登录态，
也不会因为预览出现操作入口而误导用户。

**批量查询策略（禁止 N+1）**

1. 收集当前页所有 `(reviewType, reviewId)`。
2. 一次查询可见评论总数：`WHERE (review_type, review_id) IN (...) AND status = 1 AND depth = 1 GROUP BY review_id`。
3. 一次查询各评价最新 3 条预览：用 `ROW_NUMBER() OVER (PARTITION BY review_id ORDER BY id DESC)`
   取每组前 3 条（MySQL 8 支持）。不采用"先查该页全部评论再在内存裁剪"，因为单条热门评价的评论数不可控，
   会把整页评论读进内存。
4. 一次批量解析预览评论作者的头像。
5. 拼装到 VO；任一评价无评论时给空列表与 0，不返回 null。

**兼容性**：这是新增字段，旧客户端忽略即可；服务端对缺失评论不做任何降级分支，不影响评价主体展示。

## 9. 通知设计

### 9.1 与现有通知的关系

评论通知属于"别人对你的内容作出了回应"，语义上与订单状态、工单进度、优惠券到期都不同，因此：

- 新增通知类型 `REVIEW_DISCUSSION`（17 字符，≤32 上限）。
- 新增偏好分类 `REVIEW`（展示名"评价互动提醒"）。
- `NotificationMapper.unreadCount` 的 CASE 增加 `WHEN 'REVIEW_DISCUSSION' THEN 'REVIEW'`，
  否则偏好开关对评论通知无效。
- 落库复用 `SystemNotificationWriter`，不新增写入路径。

### 9.2 触发规则（通知谁）

| 场景 | 接收人 | 标题 |
| --- | --- | --- |
| 学生评论评价 | 评价作者（评论者本人除外） | 有人评论了你的评价 |
| 学生回复楼内评论 | 被回复评论的作者（回复者本人除外） | 有人回复了你的评论 |
| 商家角色评论或回复 | 同上，接收人规则不变 | 商家评论了你的评价 / 商家回复了你的评论 |

商家角色不是独立的接收场景，只影响标题文案：作者角色为管理角色时，标题加"商家"前缀并在正文标注官方身份。

- 自己评论自己的评价、自己回复自己，不发通知。
- 只通知一层：回复 A 时只通知 A，不再额外通知评价作者，避免一次互动产生两条提醒。
- 被屏蔽、已撤回的评论不产生通知；通知发送在事务提交后，失败只记日志，不回滚评论。
- 本期不做"同一评价多次评论合并为一条"，逐条通知靠事件号幂等去重；
  若上线后反馈打扰，再在 notification-service 加 30 分钟合并窗口。

### 9.3 请求与幂等

```
POST /internal/notifications/review-discussion   （X-Internal-Token）
{
  "recipientId": 9,
  "eventId": "review-discussion:6b2f...:v1",
  "title": "有人评论了你的评价",
  "content": "李同学：我也吃过，出餐确实快",
  "reviewType": "COMMENT",
  "reviewId": "a1111...",
  "discussionId": "6b2f..."
}
```

- 事件号 `review-discussion:{discussionId}:v{version}`：同一版本重试只落一条。
- `biz_type` 写 `COMMENT` 或 `WINDOW_REVIEW`，`biz_id` 写评价公开标识，供消息中心跳转。
- 通知正文截断沿用 `SystemNotificationWriter` 的既有规则，不在评论服务里自行截断。

### 9.4 前端跳转

- 通知列表按 `bizType` 显示"查看评价"，点击跳转评价详情并定位评论区。
- 需要前端支持带 `commentId` / `reviewId` 的评价详情参数（当前列表页按 productId/windowId 进入，需补一个定位参数）。
- 该换算逻辑属于前端改动，设计上要求"通知只带评价标识与评论标识，不带业务无关的展示字段"。
- 实际落库只使用通知表既有的 `biz_type` 与 `biz_id`：`biz_type` 写评价类型，
  `biz_id` 写评价公开标识；`discussionId` 随请求传入但当前通知表没有对应列，**不落库**。
  因此学生端点击通知只能定位到该评价的评论区，不能直接高亮具体楼层。
  若后续需要精确定位，必须先给通知表增加评论标识列并同步迁移，不能由前端猜。
  学生端已按此实现：`biz_type` 为 `COMMENT` / `WINDOW_REVIEW` 时跳转
  `pages/review/discussions?reviewType=...&reviewId=...`。

## 10. 防刷、幂等与并发

### 10.1 三层防护

| 层 | 手段 | 作用 |
| --- | --- | --- |
| 入口限流 | Redis 固定窗口计数 | 拦截脚本刷屏与误触连发，降低无效写库 |
| 并发串行 | `DistributedLockService.tryAcquire` | 同一用户对同一评价的并发提交只放行一个 |
| 最终幂等 | `uk_review_discussion_request(author_id, request_key)` | 网络重试、客户端重复提交只落一条 |

三层缺一不可：限流是体验保护，锁是并发保护，唯一键是数据正确性的最后防线。

### 10.2 限流参数

基于 `RedisOperationsService.increment(key, Duration)`（原子自增且只在首次设置 TTL，不会因持续请求延长窗口）：

| Key | TTL | 阈值 | 超限行为 |
| --- | --- | --- | --- |
| `review:discussion:rate:user:{authorId}` | 60 秒 | 10 条 | 429 |
| `review:discussion:rate:user-review:{authorId}:{reviewId}` | 60 秒 | 3 条 | 429 |
| `review:discussion:rate:review:{reviewId}` | 60 秒 | 60 条 | 429（热门评价全站防洪） |

- 计数在写库前自增；写库失败不回退计数（宁可少发几条，也不做补偿式回退导致计数失真）。
- 阈值集中定义在常量类，不散落在 Service 内，便于后续调整。
- 管理角色的阈值可与学生不同（例如商家 20 条/分钟）；若本期不做区分，统一按上表执行并在文档标注。

### 10.3 分布式锁

```java
// 同一用户对同一评价的提交串行化，锁键不含 request_key：
// 双击产生的是两个不同的 request_key，唯一键无法拦截，必须靠锁兜住。
String lockKey = "review:discussion:submit:" + authorId + ":" + reviewId;
try (DistributedLockService.LockHandle handle = locks.tryAcquire(lockKey)) {
    if (handle == null) {
        throw new BusinessException(CONFLICT, "操作过于频繁，请稍后再试");
    }
    // 校验评价可见性 → 限流计数 → 解析楼层归属 → 写库
}
```

- `tryAcquire` 拿不到锁时**立即失败**，不排队等待，避免请求堆积。
- 锁只包住"校验 + 写库"，不包远程调用与通知，持有时间控制在毫秒级。
- 锁租期由 `DistributedLockService` 的默认实现决定，业务不得自行删除锁键。

### 10.4 并发场景处理

| 风险 | 处理 |
| --- | --- |
| 同一请求键重复提交 | 唯一键冲突后回读原记录；已发布且内容一致返回原评论，内容不一致或原记录已撤回/屏蔽返回 409 |
| 双击产生两个请求键 | 分布式锁串行化；第二个请求在锁内重新校验限流，必要时拒绝 |
| 两人同时回复同一楼层 | 无冲突，各自插入；`root_id` 来自已提交的楼层根，不依赖计数 |
| 编辑与撤回并发 | `UPDATE ... WHERE discussion_id=? AND author_id=? AND status=1 AND version=?`，未命中返回 409 |
| 屏蔽与作者编辑并发 | 屏蔽条件 `WHERE status = 1`；作者编辑条件同样要求 `status = 1`，先到者生效，后到者 409 |
| 评价在评论过程中被撤回 | 写入前校验评价 `status = 1`；极小时间窗内可能写入孤儿评论，读取按评价状态过滤，永不展示 |
| 楼层根被撤回后有人继续回复 | 回复前校验楼层根 `status = 1`，已撤回返回 409 |
| 通知重复 | 通知表唯一键 `(recipient_id, event_id)` |

### 10.5 Redis 故障降级（限流放行、锁拒绝）

两类组件方向相反，实现时已按此落地：

| 组件 | Redis 故障时 | 理由 |
| --- | --- | --- |
| 限流计数 | **fail-open**，记 WARN 后继续写库 | 限流只是防护措施，缺失只降低防刷强度；唯一键与业务校验仍在 |
| 分布式锁 | **fail-closed**，返回 409 让用户重试 | 公共组件 `DistributedLockService.tryAcquire` 在等待超时与 Redis 故障时都返回 null，业务层无法区分；若统一放行，并发双击仍会产生两条不同请求键的记录，锁就形同虚设 |

- 锁 fail-closed 的代价：Redis 故障时评论写入不可用，但评价与评论的**读取不受影响**，
  用户仍能浏览；写入口本身依赖 MySQL，Redis 整体故障时通常也伴随其他异常，这个取舍可以接受。
- 不允许把 Redis 异常吞掉后静默成功：必须落 WARN 日志，便于告警与事后分析。

### 10.6 消费准入缓存

准入查询在每次发表评论时发生，必须缓存，否则评价区一有热度就会把 order-service 打满：

| 缓存键 | 值 | TTL | 说明 |
| --- | --- | --- | --- |
| `review:discussion:eligible:{userId}:{targetType}:{targetId}` | `1` | 5 分钟 | 已消费。`COMPLETED` 不可逆，结果稳定，可缓存较久 |
| 同上 | `0` | 30 秒 | 未消费。用户可能刚完成订单，TTL 必须短，避免"刚取餐却不能评论" |

- 只缓存布尔结果，不缓存订单明细或金额，缓存泄露也不暴露交易数据。
- 只有 order-service 明确返回成功时才写缓存；超时、异常、业务失败一律不写，避免把故障状态固化成"未消费"。
- 缓存键包含 `userId` 与目标编号，不能由客户端指定，也不接受客户端传入的"已消费"标记。
- Redis 不可用时直查 order-service；直查也失败则按 7.2 返回 503，不降级放行。
- 不做缓存预热与主动失效：订单完成事件不通知评论服务（避免新增 MQ 依赖），
  依赖短 TTL 自然收敛；30 秒的最坏延迟在评论场景可以接受。

## 11. 内容治理

- 长度：1 至 500 字（按 Unicode 字符计），前后空白归一化后校验；纯文本，前端按文本渲染，不解释 HTML。
- 禁止字段：客户端提交的 `authorId`、`authorRole`、`authorName`、`reviewType`、`reviewId`、`rootId`、`status`
  一律忽略或以服务端事实覆盖，防止越权伪造。
- 屏蔽：管理员按第 6.7 节操作；屏蔽不删除正文，保留追溯能力。
- 举报：本期不做学生举报入口；若后续需要，新建 `review_discussion_report` 表，不与评论表混存。
- 敏感词：本期不接第三方内容审核，也不在评论服务内维护词库；如上线后必须做，另立方案，
  不在写链路里同步调用外部审核服务（会放大评论提交的失败率与延迟）。

## 12. 影响面与兼容性

- **数据库**：comment_db 新增 1 张表；`comments`、`window_reviews`、评分表零改动。
- **接口**：comment-service 新增 9 个接口方法。两类评价各 2 个顶层读写路径（发表/分页），
  楼层回复、编辑、撤回、屏蔽共 5 个路径两类评价共用。
- **跨服务**：order-service 新增 1 个内部消费事实查询端点（`X-Internal-Token`，只做 EXISTS，不返回订单明细）；
  comment-service 新增 1 个 Feign 方法与准入缓存。这是本期唯一跨服务改动。
- **VO**：`CommentVO`、`WindowReviewVO` 各新增 2 个字段（向后兼容）。
- **消息服务**：新增 1 个内部端点、1 个通知类型、1 个偏好分类、1 处未读映射。
- **前端**：学生端评价卡片增加评论区入口与评论页；通知列表增加跳转分支。
  管理端增加商家回复与屏蔽入口（另仓）。
- **性能**：评价列表每页最多 50 条，评论计数与预览各一次批量查询，无逐条远程调用。
- **评分**：评论完全不参与评分汇总、排序、推荐与评分 outbox，无 MQ 变更。

## 13. 实施进度

后端源码与学生端界面已完成，开发库表结构已迁移；管理端与运行验证未完成：

1. [已完成] order-service 内部消费事实接口：`PurchaseFactMapper` 的两个 EXISTS 查询 + `GET /internal/orders/purchase-facts`。
2. [已完成，已执行] 迁移脚本 `V6__create_review_discussion.sql` + `ReviewDiscussionDO/Mapper`（含唯一键冲突回读）。
   2026-11-26 开发库 9095 可用后，已通过 MySQL 容器执行脚本并核验：`review_discussion` 建表成功、共 17 列，
   主键与 `uk_review_discussion_public`、`uk_review_discussion_request` 两个唯一键、
   三个普通索引与三条 CHECK 约束均存在；`comment_service` 账号已有 `comment_db.*` 的 SELECT/INSERT/UPDATE 授权。
   回滚方式不变：先下线评论接口，再 `DROP TABLE review_discussion`，不影响评价与评分数据。
3. [已完成] `ReviewDiscussionService`：发表、回复、编辑、撤回、屏蔽、顶层分页、楼层分页、批量汇总。
4. [已完成] 准入校验：`DiscussionEligibilityService` + 布尔缓存（true 5 分钟 / false 30 秒）+ 403/503 语义。
5. [已完成] 防刷：`DiscussionGuard` 三档固定窗口限流 + 提交锁（复用 `common-redis`，未新增基础设施）。
6. [已完成] `ReviewDiscussionController` 与 VO；`CommentVO` / `WindowReviewVO` 扩展与批量拼装。
7. [已完成] 通知：`REVIEW_DISCUSSION` 类型、`REVIEW` 分类、未读映射、内部端点、评论成功后调用。
8. [已完成，未构建] 学生端（uni-app，另一仓库）：评价卡片评论摘要与入口、独立评论页
   `pages/review/discussions`（顶层分页、楼层回复、发表、回复、编辑、撤回、商家标识）、
   通知中心跳转评论区。接口层补齐评论解析与幂等键；仅完成静态脚本与 Node 行为校验，
   未运行 HBuilderX 构建、未连真实后端。
9. [未开始] 管理端：商家回复与屏蔽入口（另仓）。

## 14. 测试要点

- 发表：首次成功、同键同内容幂等、同键不同内容 409、评价不存在 404、评价已撤回 404。
- 回复：回复楼层根、回复楼内回复（`depth` 仍为 2）、被回复评论已撤回 409、跨评价回复被拒绝。
- 编辑：本人成功、他人 403、版本冲突 409、编辑不产生新通知、编辑不改变楼层位置。
- 撤回：本人成功、重复撤回幂等、撤回楼层根后整层不可见、撤回后不可再被回复。
- 屏蔽：四类管理角色 × 本档口/他人档口/全平台；学生屏蔽他人 403；重复屏蔽幂等。
- 准入：未消费 403；买过该菜品可评论其菜品评价；只买过同店铺其他菜品时可评论店铺评价、但不能评论该菜品评价；
  已退款/已取消/未取货订单不算消费；管理角色跳过校验；order-service 超时或报错返回 503，不放行。
- 准入缓存：已消费缓存 5 分钟；未消费缓存 30 秒；用户新完成订单后最多 30 秒即可评论；缓存异常时直查不误拒。
- 分页：50 条评价的评论计数 SQL 次数固定；游标跨评价使用返回 400；楼层回复正序、顶层倒序。
- 限流：用户维度、用户+评价维度、评价维度分别触发 429；窗口过期后恢复。
- 降级：Redis 不可用时限流 fail-open、评论仍可提交并记录 WARN；提交锁 fail-closed 返回 409；
  准入校验 fail-closed，order-service 不可用时返回 503；唯一键仍能拦住重复请求。
- 并发：同用户双击只落一条；两用户同时回复同一楼层各自成功；编辑与撤回并发只有一方成功。
- 通知：评论评价通知作者、回复通知被回复人、自己操作自己不通知、同版本重试不重复、偏好关闭后不推送但仍落库。
- 前端：无评论不渲染入口、预览最多 3 条、超长内容折行展示、跳转定位到评论区。

## 15. 决策记录

2026-11-25 已按下列默认值实施，代码与本文一致：

1. **D1 两层**：顶层 + 楼中楼，回复条数不限、对话可一直继续，只是不逐层缩进。
2. **D2 谁能评论**：所有登录用户，学生为主，商家角色以官方身份参与。
3. **D3 条数**：同一学生对同一评价可发多条（受每 60 秒 3 条限流约束）。
4. **D4 展示名**：学生昵称、商家档口名，均用发布时快照。
5. **D5 列表集成**：评价 VO 返回评论数与前 3 条预览。
6. **D6 通知分类**：独立 `REVIEW`。
7. **D7 屏蔽**：本期实现，档口范围内可屏蔽。
8. **D8 图片**：不支持，纯文本 1 至 500 字。
9. **D9 准入**：必须有该菜品/该店铺的已完成订单，管理角色豁免；已新增 order-service 内部接口。

确认后按第 13 节顺序落地，并同步给出迁移脚本与接口清单。
