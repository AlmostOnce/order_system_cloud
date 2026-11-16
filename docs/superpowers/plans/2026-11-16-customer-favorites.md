# 学生端店铺与菜品收藏实施计划

> **实施方式：**按用户明确要求，由当前执行者按任务顺序实现；每项修改遵循仓库既有分层与详细中文注释要求。

**目标：**让学生能够收藏窗口和菜品，在独立列表中管理并重新进入对应点餐页。

**架构：**`user-service` 在 `user_db` 保存按用户隔离的收藏关系，使用 CUSTOMER JWT 限制所有接口。uni-app x 复用商品服务现有窗口、菜单和图片接口丰富收藏条目，不跨服务查库或新增目录服务接口。

**技术栈：**Spring Boot、MyBatis-Plus、MySQL、Spring Security JWT；uni-app x / UTS、现有 `common/http.uts`、商品 API 与 `business-image`。

**设计说明：**`docs/superpowers/specs/2026-11-16-customer-favorites-design.md`

## 全局约束

- 收藏类型只有 `WINDOW` 和 `PRODUCT`；界面名称为“店铺”和“菜品”。
- 收藏关系属于 `user-service`，目标文案、图片、价格由 `product-service` 提供。
- 后端始终从 JWT `sub` 取用户 ID；所有外部收藏接口仅 CUSTOMER 可访问。
- add/delete 幂等；状态查询有界；收藏列表使用稳定 ID 游标与硬上限。
- 迁移只新增 `user_favorites`，保留目标暂不可用的收藏，不自动删除。
- 代码和重要字段必须有准确的详细中文注释；不运行 Maven、构建、测试或服务重启，不新增测试代码。
- 执行迁移前确认 `user_db`、表结构和备份；执行后读取表结构及索引验证结果。

## 重点检查的失败场景

1. 未登录、非 CUSTOMER 或伪造 userId：接口拒绝或完全忽略客户端用户归属。
2. 同一收藏快速重复提交：唯一键保证只保留一条记录，接口不报重复错误。
3. 删除不存在的收藏：幂等返回成功。
4. 非法类型、非正数 ID、空或超过100个批量 ID、非法游标和超限 limit：返回参数错误，不执行无界查询。
5. 店铺停用或菜品下架：收藏关系保留，界面展示不可用状态并允许移除。

## 文件与职责

### 后端 `E:/order_system_cloud`

- 新建 `user-service/src/main/java/com/hbue/ordering/user/controller/UserFavoriteController.java`：CUSTOMER HTTP 边界、JWT 用户 ID 和参数绑定。
- 新建 `user-service/src/main/java/com/hbue/ordering/user/service/UserFavoriteService.java`、`service/impl/UserFavoriteServiceImpl.java`：收藏幂等写入、删除、批量状态与游标分页。
- 新建 `user-service/src/main/java/com/hbue/ordering/user/model/UserFavoriteDO.java`、`mapper/UserFavoriteMapper.java`：MyBatis-Plus 持久化映射和有界 SQL。
- 新建 `user-service/src/main/java/com/hbue/ordering/user/dto/request/FavoriteStatusRequest.java`、`vo/UserFavoriteItemVO.java`、`vo/UserFavoritePageVO.java`、`vo/FavoriteStatusVO.java`：明确接口输入输出。
- 新建 `docs/database/migrations/user_db/V11__create_user_favorites.sql`：新增收藏表、唯一键和用户分页索引。
- 修改 `docs/ARCHITECTURE.md`、`docs/PROJECT_PROGRESS.md`、`docs/superpowers/plans/2026-11-02-student-client-next-steps.md`：记录归属、迁移与计划状态。

### 学生端 `E:/order_system_cloud_user`

- 新建 `api/favorites.uts`：封装新增、删除、状态与游标查询及响应解析。
- 修改 `pages/home/home.uvue`：窗口收藏入口及状态，不冒泡至卡片导航。
- 修改 `pages/menu/menu.uvue`：菜品收藏入口及状态，不触发评价和购物操作。
- 修改 `pages/mine/mine.uvue`、`pages.json`：CUSTOMER“我的收藏”入口和列表路由。
- 新建 `pages/favorites/list.uvue`：店铺/菜品标签、游标加载、刷新、下架占位、移除和点餐定位。
- 新建 `static/favorites/heart-outline.svg`、`static/favorites/heart-filled.svg`；在 `styles/account.css` 增加收藏页共享视觉类，沿用已维护的 DESIGN/UX 约定。
- 修改 `UX-CONTRACT.md`：记录收藏动作、列表导航、错误和下架行为。

## 执行步骤

### 任务1：实现 user-service 收藏 API 与持久化

- 在 V11 迁移中建立 `user_favorites`：自增 `favorite_id`、`user_id`、`target_type`、`target_id`、`created_at`；唯一键 `(user_id,target_type,target_id)`，列表索引 `(user_id,target_type,favorite_id)`。
- 使用 MyBatis-Plus DO/Mapper；新增 SQL 使用 MySQL upsert 保证收藏幂等；删除不存在记录按成功处理。
- 暴露设计说明中的四个接口。校验允许类型、正数 ID、批量最多100项、分页默认20且最多50、游标为正数。
- 用 SQL `favorite_id < beforeId ORDER BY favorite_id DESC LIMIT limit+1` 实现稳定游标；服务层去掉探测行并返回 `hasMore/nextCursor`。
- Controller 仅从 JWT `sub` 取用户 ID，并在类/方法层限制 CUSTOMER；响应不包含其他用户信息。

### 任务2：执行并核验 user_db 迁移

- 核对 `.env` 指向的开发连接和 `user_db`，确认既有表/字段与目标表状态；迁移前生成并验证可读的 `user_db` 备份。
- 执行 V11 后读取列、唯一键、分页索引及存储引擎，确认空表结构正确且重复执行不会破坏已有收藏。

### 任务3：接入学生端收藏操作和收藏列表

- 用新 `api/favorites.uts` 管理收藏关系；状态查询只提交当前页面可见的 ID，列表按游标读取。
- 首页卡片和点餐卡片的图标操作与父级点击分离；操作等待服务端成功后更新状态，错误保留原值并显示 Toast。
- “我的收藏”只对 CUSTOMER 显示，两个标签分别查询窗口和菜品。窗口数据用现有 `listActiveWindows()`；菜品详情用新增的 `fetchProductImages()` 区分目录缺项和网络错误，再按当前页涉及的窗口调用 `fetchWindowMenu()`，不对每个收藏菜品逐条请求。
- 当前目录中不存在的目标显示“暂不可用”并保留删除入口。可用窗口跳转菜单页；可用菜品跳转时携带现有 `windowId`、`productId` 参数。
- 页面包含首屏加载、空状态、错误重试、下拉刷新、加载更多、重复请求保护及离页请求失效保护。

### 任务4：更新文档并静态复核

- 更新架构职责、项目进度和学生端待办状态；保留工作区中原有的统计文档、首页和登录页未提交修改。
- 执行 `git diff --check`、迁移 SQL 静态审读、页面 JSON/UTS 接口契约与模板调用点对照；不运行 Maven、前端构建、测试或重启服务。
- 若 HBuilder V/Android 真机运行需要新版服务代码，记录需由用户重新运行的服务与端点；不宣称未执行的运行验证已通过。

## 当前执行状态

- [x] 用户确认收藏归 user-service，类型为店铺窗口与菜品，并授权按计划继续实施。
- [x] 完成后端源码与迁移脚本；后端源码未执行 Maven、测试或启动检查。
- [x] 备份并执行开发库迁移；已核验 `user_db.user_favorites` 列、引擎、排序规则和索引，初始行数为0。
- [x] 完成学生端收藏 UI/API 与目录详情补充；未做前端构建或真机联调。
- [x] 更新项目进度与架构文档，并完成静态复核；运行期验证留待用户启动服务后进行。

## 执行记录（2026-11-16）

- 迁移前检查：开发库可连、版本 MySQL 8.0.46，`user_favorites` 不存在，现有4张表；使用 user-service 配置账号完成备份。
- 迁移备份位于系统临时目录：`C:\Users\DELL\AppData\Local\Temp\order_system_cloud_user_db_before_favorites_v11_20261116.sql`，17,905 字节；SHA-256：`B18554B34C0EBC25C8396D66B1ECDD88C72B3F193A2DD2869E0D5D36F3EE6E43`。
- V11 执行成功；核验 InnoDB、`utf8mb4_unicode_ci`、`favorite_id/user_id/target_type/target_id/created_at` 五列、主键、`(user_id,target_type,target_id)` 唯一键、`(user_id,target_type,favorite_id)` 分页索引及0条初始记录。
- 仅做源码静态审阅与 SQL/迁移核验；未运行 Maven、构建、测试、服务重启、HBuilder 编译或真机联调。
