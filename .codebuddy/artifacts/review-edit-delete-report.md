# 评价修改与删除功能实现报告

> 范围：comment-service（菜品评价、店铺评价）+ comment-api（评分事件契约）+ comment_db / product_db 迁移。
> 未运行 Maven、未编译、未启动服务、**迁移脚本尚未执行**（本机无 mysql 客户端与 docker，见第 6 节）。

---

## 1. 权限模型（按需求落地）

| 操作者 | 能操作的评价 |
| --- | --- |
| 评价作者本人（CUSTOMER） | 自己的评价，不查任何数据范围 |
| 系统管理员（SYSTEM_ADMIN） | 任意评价 |
| 总经理（GENERAL_MANAGER） | 任意评价（需求中"非总经理"才受窗口范围限制） |
| 餐厅经理（RESTAURANT_MANAGER） | 数据范围覆盖的餐厅下各档口的评价 |
| 档口负责人（STALL_MANAGER） | 数据范围内档口的评价 |
| 其他/未登录 | 只能操作自己发出的评价，否则 403 |

判定顺序：**作者 → 系统管理员/总经理 → 受限管理角色的数据范围**。顺序不能调换，否则作者会被角色判定误拒。

数据范围判定复用既有基础设施：

- `user-service` 的 `GET /internal/users/{userId}/scopes`（新增 `CommentScopeClient`），Redis 缓存键与订单、商品服务共用 `authz:user-scope:{userId}`，TTL 300 秒。
- `product-service` 的 `GET /internal/windows/{windowId}`（新增 `CommentWindowClient`）把档口换算为所属餐厅，仅在用户确实持有餐厅范围时才调用。
- 无法确认范围或档口归属时返回 **503**，不降级放行；无权限返回 **403**（评价是公开资源，无需用 404 隐藏）。

两个客户端放在 `com.hbue.ordering.comment.client` 而非复用 `user-api` / `product-api`，因为本服务的
`@EnableFeignClients` 只扫描自身 client 包；这与 support-service 自建 `SupportUserScopeClient`、
`SupportWindowClient` 的做法一致，也避免为两个接口引入新的模块依赖。

## 2. 新增接口

| 方法 | 路径 | 角色门禁 | 说明 |
| --- | --- | --- | --- |
| PUT | `/comments/{commentId}` | 五类角色 | 修改菜品评价的评分、正文、图片、标签 |
| DELETE | `/comments/{commentId}` | 五类角色 | 软删除菜品评价 |
| PUT | `/comments/window-reviews/{reviewId}` | 五类角色 | 修改店铺评价 |
| DELETE | `/comments/window-reviews/{reviewId}` | 五类角色 | 软删除店铺评价 |

`@PreAuthorize` 只做"这类接口能不能进"，"这一条能不能改"由 `ReviewAuthorityService` 判定。

修改参数语义（两类评价一致）：

- `score` 必填，避免"只想改文字却把评分清零"；
- `content` 为 null 保持原文，传空串表示清空；
- `imageFileKeys` / `tags` 为 null 保持原值，传空数组表示清空；
- 订单、菜品、档口不可改，保证"确实消费过"的结论不被编辑绕过；
- 只校验本次**新增**图片的归属，评价里原有图片允许原样保留。

## 3. 数据模型变更

`docs/database/migrations/comment_db/V5__review_edit_and_delete.sql`

- `comments` 新增 `updated_at`；`ck_comment_status` 由 `status = 1` 放宽为 `status IN (1,2)`（1=已发布，2=已删除）。
- `comment_rating_stats` 约束由 `review_count > 0` 放宽为 `review_count >= 0`，并保留
  `score_total <= review_count * 5`、`review_count > 0` 时 `score_total >= review_count` 的关系，
  支持"某菜品评价被全部删除"的零值状态。
- `window_reviews` 新增 `status`、`updated_at`、`ck_window_review_status`，以及
  `(window_id,status,id)`、`(user_id,status,id)` 两个索引。

`docs/database/migrations/product_db/V14__allow_zero_rating.sql`

- `product_rating_stats.ck_product_rating` 同步放宽为允许零值，否则商品服务会拒收合法的清零快照。

两个脚本都按 `information_schema` 判断后再执行，可重复运行。

## 4. 业务实现要点

### 4.1 菜品评价（MySQL 元数据 + MongoDB 正文）

- 修改顺序：**先改 Mongo 正文 → 再走元数据本地事务**。元数据事务负责
  `score`/`has_images`/`updated_at`、评分汇总增量、评分事件 Outbox；失败时把正文补偿回修改前，
  避免"提示失败但内容已变"。
- 评分变化才调整汇总并推送快照；仅改文字不产生下游事件。
- 删除为软删除：`status=2`，正文与评分原值保留；`byRequest`、`byOrder`、分页、汇总全部按 `status=1` 过滤。
- `byPurchase` 刻意不带状态过滤：唯一键 `uk_comment_purchase` 对已删除评价同样成立，必须能查到它才能区分
  "已评价后删除"和"从未评价"。

### 4.2 店铺评价（单库单事务）

- 修改一次 UPDATE 写入评分、正文、图片、标签，`has_images` 由生成列自动维护。
- 变的字段才写：内容与评分都没变化时直接返回原视图，避免把"无变更"误报成并发冲突。
- 删除走 `status=2`，评价立即从列表、评分、汇总中消失。

### 4.3 评分事件契约

`ProductRatingChangedEvent.valid()` 支持零评价快照（`reviewCount=0` 时要求 `scoreTotal=0`），
下游 `ProductRatingProjection` 按版本覆盖，删除最后一条评价后商品评分回到"暂无评分"。

事件编号改为每次变更生成新 UUID：同一条评价在新增、修改、删除过程中会产生多条事件，
沿用评价标识会撞主键。

## 5. 前端边界

按既有约定，学生端（uni-app）只承载 CUSTOMER 的"改/删自己的评价"；管理端的审核、代改、代删
由 Vue 管理端调用同一组接口，权限由后端接口权限 + 数据范围双重保障，不依赖前端隐藏入口。

## 6. 验证情况

已完成：

1. 全仓 `com.hbue.*` 导入可解析，包声明与目录一致。
2. IDE 诊断（error/warning）对 comment-service、comment-api 无报错。
3. 新增/调整单测：
   - `ReviewAuthorityServiceImplTest`：作者本人、普通学生越权、系统管理员、总经理、餐厅经理覆盖/未覆盖、档口负责人直接授权 6 类判定；
   - `CommentServiceTest`：修改时"先正文后元数据"、元数据失败回滚正文、删除只改元数据；
   - `WindowReviewServiceTest`：修改时省略字段保持原值/空数组清空、删除走软删除；
   - `CommentValidationTest`：零评价快照合法、零条却带总分被拒。

未完成（需你在本地执行）：

- **迁移脚本未执行**：本机 PATH 中没有 `mysql` 客户端，也没有 docker，无法连接 `127.0.0.1:9095`。
  请在确认目标库后执行：
  `mysql -h 127.0.0.1 -P 9095 -u comment_service -p < docs/database/migrations/comment_db/V5__review_edit_and_delete.sql`
  `mysql -h 127.0.0.1 -P 9095 -u <product 库账号> -p < docs/database/migrations/product_db/V14__allow_zero_rating.sql`
- 编译与单元测试未运行（项目约束禁止擅自使用 Maven）。

## 7. 已知限制

1. 评价删除后，唯一键仍占用原订单，同一订单无法再次评价；再次提交会返回"该订单已经评价过"。
   如需支持"删除后重新评价"，需要引入"复活已删除记录"的语义，可后续单独评审。
2. 菜品评价跨两个存储：正文在 Mongo、评分在 MySQL。极端情况下（Mongo 已改、MySQL 事务失败且补偿也失败）
   会出现正文与评分不一致，日志会打出 `commentId` 供人工核对；未引入分布式事务。
3. 管理端目前只能修改/删除单条评价，没有"按档口批量查看含已删除评价"的审核列表，需要时可另开接口。
