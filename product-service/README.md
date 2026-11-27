# product-service 商品服务

> 商品目录域服务：餐厅、档口、分类、菜品与单选规格的管理，顾客菜单查询，订单核价（报价），评分投影消费，搜索/推荐数据源。默认端口 `9093`，数据库 `product_db`。缓存 + 布隆过滤器加速菜单读取；目录变更经本地消息表驱动搜索/推荐刷新。不接入 Seata；库存事实只读转发库存服务。

## 模块定位与职责边界

- 拥有商品目录事实：餐厅/档口/分类/菜品/规格的全部状态变更。
- 报价是订单价格唯一事实源：服务端核价并生成规格快照，不信任调用方金额。
- 不负责库存事实（只聚合展示）、评论事实（评分快照由评价服务经事件投影）。
- `product-api` 为共享契约模块：核价客户端、档口/餐厅内部查询、搜索源、推荐源 4 个 Feign 客户端与降级工厂。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）/ Validation / Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Sentinel（降级规则持久化在 Nacos，dashboard 127.0.0.1:9099） |
| 数据 | MyBatis-Plus 3.5.8 + Druid（max-active=10）+ MySQL `product_db`（默认 `127.0.0.1:9095`） |
| 缓存 | Redis + Redisson（菜单缓存、布隆过滤器、分布式锁） |
| 消息 | Spring Cloud Stream RocketMQ：生产搜索刷新、消费评分事件 |

- `spring.profiles.include: search-mq, comment-mq`；搜索事件延迟 1 秒发布、同步发送、超时 3 秒。
- 评分消费 binding：`max-attempts=1` + 显式重抛，交 Broker 重投 16 次。
- Sentinel 降级规则 Data-ID `product-service-degrade-rules`；`SWAGGER_ENABLED` 控制文档。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `RestaurantAdministrationController/Impl` | 餐厅管理（新建/停用/排序需总经理；经理仅可改本餐厅展示字段） |
| `ProductAdministrationController/Impl` | 档口/分类/菜品 CRUD + 图片，统一数据范围校验与 Outbox 写入 |
| `ProductImageController` / `WindowImageGallery` | 店铺图库（≤5 张有序、首图封面）、菜品图，文件键归属校验 |
| `ProductSpecificationController/Impl` | 规格完整替换（行锁串行 + 全量校验） |
| `ProductMenuController/Impl` | 顾客餐厅/窗口/菜单查询（多级缓存与回源） |
| `ProductStockController/Impl` | 聚合窗口库存（Feign 转发库存服务） |
| `ProductRatingController` | 商品评分快照查询（投影表） |
| `ProductQuoteServiceImpl` | 核价与规格加价计算（只读事务，REPEATABLE_READ） |
| `ProductInternalController` / `InternalCatalogController` / 推荐源控制器 | 报价、搜索文档快照、推荐快照与候选复核、档口/餐厅内部查询 |
| `ProductMenuCacheService` / `ProductBloomFilterManager` / `ProductCacheKeys` | 菜单缓存、布隆过滤器、键管理 |
| `DefaultDataScopeService` | 向用户服务查数据范围并短缓存（TTL 300 秒） |
| `ProductSearchOutboxWriter` / `ProductSearchOutboxPublisher` | 同事务写 Outbox + 定时发布与每日清理 |
| `ProductRatingMessaging` / `ProductRatingProjection` | 评分事件消费与单调版本投影 |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/product_db/`（V1–V13）。

### restaurants（餐厅/食堂）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| restaurant_id | BIGINT | NOT NULL，自增 | 主键 | 餐厅主键 |
| restaurant_code | VARCHAR(32) | NOT NULL | uk_restaurants_code | 餐厅编码 |
| restaurant_name | VARCHAR(64) | NOT NULL | — | 餐厅名称 |
| location | VARCHAR(64) | NOT NULL，默认 '' | — | 餐厅位置（楼层） |
| phone | VARCHAR(20) | 可空 | — | 联系电话 |
| image_file_key | CHAR(32) | 可空 | — | 门面图文件键（文件服务管理） |
| notice | VARCHAR(255) | 可空 | — | 公告 |
| sort_order | INT | NOT NULL，默认 0 | — | 展示排序 |
| status | TINYINT UNSIGNED | NOT NULL，默认 1 | CHECK 限 (0,1) | 状态：1 启用、0 停用 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

### dining_windows（档口/窗口）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| window_id | BIGINT | NOT NULL，自增 | 主键 | 档口主键 |
| restaurant_id | BIGINT | NOT NULL，默认 1 | 外键 → restaurants；索引首列 | 所属餐厅 ID |
| window_code | VARCHAR(32) | NOT NULL | uk_dining_windows_code | 档口编码 |
| window_name | VARCHAR(64) | NOT NULL | — | 档口名称 |
| description | VARCHAR(500) | 可空 | — | 窗口简介 |
| location | VARCHAR(255) | 可空 | — | 位置 |
| image_file_key | CHAR(32) | 可空 | — | 封面图文件键 |
| image_file_keys | JSON | 可空 | CHECK：NULL 或长度 ≤5 的 JSON 数组 | 店铺有序图片数组，首图为封面 |
| is_open | TINYINT | NOT NULL，默认 1 | — | 营业口径临时开关（备餐/休息），与管理状态独立 |
| sort_order | INT | NOT NULL，默认 0 | CHECK ≥0 | 展示排序 |
| status | TINYINT UNSIGNED | NOT NULL，默认 1 | CHECK 限 (0,1) | 管理口径状态，停用后不可接单 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 外键：`fk_dining_windows_restaurant`（RESTRICT）。

### product_categories（菜品分类）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| category_id | BIGINT | NOT NULL，自增 | 主键 | 分类主键 |
| window_id | BIGINT | NOT NULL | 两枚唯一键 + 外键 → dining_windows | 所属档口 |
| category_name | VARCHAR(64) | NOT NULL | uk_product_categories_window_name | 分类名称，同档口唯一 |
| sort_order | INT | NOT NULL，默认 0 | — | 排序 |
| status | TINYINT UNSIGNED | NOT NULL，默认 1 | CHECK 限 (0,1) | 状态 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 唯一键：`uk_product_categories_window_name(window_id, category_name)`、`uk_product_categories_window_category(window_id, category_id)`；索引 `(window_id, status, sort_order)`。

### products（菜品）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| product_id | BIGINT | NOT NULL，自增 | 主键 | 菜品主键 |
| window_id | BIGINT | NOT NULL | uk_products_window_code；外键 → dining_windows | 所属档口 |
| category_id | BIGINT | 可空 | 复合唯一键与外键参与列 | 所属分类 |
| product_code | VARCHAR(64) | NOT NULL | uk_products_window_code | 菜品编码，同档口唯一 |
| product_name | VARCHAR(128) | NOT NULL | — | 菜品名称 |
| description | VARCHAR(500) | 可空 | — | 菜品描述 |
| image_file_key | CHAR(32) | 可空 | — | 菜品图片文件键 |
| price | DECIMAL(10,2) | NOT NULL | CHECK ≥0 | 售价 |
| sale_status | TINYINT UNSIGNED | NOT NULL，默认 0 | CHECK 限 (0,1) | 售卖状态：1 上架、0 下架 |
| sort_order | INT | NOT NULL，默认 0 | — | 排序 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 索引：`(window_id, sale_status, sort_order)`、`(window_id, category_id, sale_status, sort_order)`。

### product_specification_groups（规格组）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| group_id | BIGINT | NOT NULL，自增 | 主键 | 规格组编号，完整替换保留已有编号 |
| product_id | BIGINT | NOT NULL | uk_specification_groups_active_name；外键 → products | 所属菜品，只能归属一个菜品 |
| name | VARCHAR(50) | NOT NULL | — | 规格组名称，启用组同菜品内唯一 |
| is_required | TINYINT UNSIGNED | NOT NULL，默认 0 | CHECK 限 (0,1) | 是否必选：每组只选一个选项 |
| sort_order | INT | NOT NULL，默认 0 | CHECK ≥0 | 展示排序 |
| status | TINYINT UNSIGNED | NOT NULL，默认 1 | CHECK 限 (0,1) | 启用状态，移除只停用不删除 |
| active_name | VARCHAR(50) | STORED 生成列 `status=1 ? name : NULL` | uk_specification_groups_active_name | 仅启用记录参与名称唯一约束，停用历史可重名 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 索引：`(product_id, status, sort_order, group_id)`。

### product_specification_options（规格选项）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| option_id | BIGINT | NOT NULL，自增 | 主键 | 选项编号，学生核价只提交该编号 |
| group_id | BIGINT | NOT NULL | uk_specification_options_active_name；外键 → 规格组 | 所属规格组，禁止跨组移动 |
| name | VARCHAR(50) | NOT NULL | — | 选项名称，启用项同组内唯一 |
| additional_price | DECIMAL(10,2) | NOT NULL，默认 0.00 | CHECK ≥0 | 在基础售价上累加的非负金额 |
| sort_order | INT | NOT NULL，默认 0 | CHECK ≥0 | 展示排序 |
| status | TINYINT UNSIGNED | NOT NULL，默认 1 | CHECK 限 (0,1) | 启用状态，移除只停用不删除 |
| active_name | VARCHAR(50) | STORED 生成列 `status=1 ? name : NULL` | uk_specification_options_active_name | 仅启用记录参与名称唯一约束 |
| created_at / updated_at | DATETIME | NOT NULL，自动维护 | — | 创建/更新时间 |

- 索引：`(group_id, status, sort_order, option_id)`。

### product_search_outbox（搜索刷新事件）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | VARCHAR(36) | NOT NULL | 主键 | 搜索刷新事件 UUID |
| window_id | BIGINT | NOT NULL | 窗口索引参与 | 需要刷新索引的窗口 |
| product_id | BIGINT | 可空 | — | 菜品 ID；为空表示刷新整个窗口 |
| attempts | INT | NOT NULL，默认 0 | — | 发布失败重试次数 |
| next_attempt_at | TIMESTAMP | NOT NULL，默认当前时间 | 待发布扫描索引参与 | 下次允许发布时间 |
| published_at | TIMESTAMP | 可空 | 待发布扫描索引参与 | Broker 确认接收时间 |
| last_error | VARCHAR(128) | 可空 | — | 最近一次发布错误类型 |
| created_at | TIMESTAMP | NOT NULL，默认当前时间 | 索引参与 | 事件创建时间 |

- 索引：`(published_at, next_attempt_at, created_at)`、`(window_id, created_at)`。

### product_rating_stats（评分投影，事实源在评价服务）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| product_id | BIGINT UNSIGNED | NOT NULL | 主键（非自增） | 菜品 ID |
| review_count | BIGINT | NOT NULL | — | 已发布评价总数 |
| score_total | BIGINT | NOT NULL | — | 评分总和 |
| revision | BIGINT | NOT NULL | — | 已应用的源评分版本，重复与乱序消费保护 |

- CHECK：`ck_product_rating`（评价数 >0 且 评价数 ≤ 分数总和 ≤ 评价数×5 且 revision >0）。

无物理删除：窗口/分类/菜品/规格统一软停用（status/sale_status 置 0）。

## 核心业务流程

### 管理端变更（`ProductAdministrationServiceImpl`）

1. 每个方法先做数据范围校验（越权 403；范围事实来自用户服务并短缓存）。
2. 写库，唯一键冲突转 409。
3. 同事务写搜索刷新 Outbox。
4. 事务提交后（afterCommit）才删菜单缓存并补写布隆过滤器——缓存旁路「先写库后删缓存」，防止旧值回写；新增条目补写布隆。

### 图片管理

图库整体替换：≤5 张、去重；新增键逐个经文件服务校验上传者/类型/归属；封面与图库 JSON 同一条 UPDATE 写入；兼容旧单图封面入口。

### 顾客菜单（`ProductMenuServiceImpl.getWindowMenu`）

布隆过滤器排除一定不存在的窗口 → 空值标记（60 秒）→ 读缓存 → 未命中抢 Redisson 窗口锁（wait 3s / lease 30s）→ 双重检查 → 回源（启用窗口 + 启用分类 + 在售菜品）→ 组装规格 → 写缓存（TTL 300 秒 + 0–120 秒抖动防雪崩）；未抢到锁直接查库返回不写缓存。

### 报价（`ProductQuoteServiceImpl.quote`）

1. 校验行数 ≤100、每行选项 ≤10、同菜同规格组合唯一、窗口启用。
2. 布隆过滤菜品 ID → 查在售菜品与启用分类。
3. 规格计价：每组最多选 1、必选不能为空、加价非负、总价上限；生成升序规格键与规格快照 JSON。
4. 结果不缓存，事务内快照读保证一致性。

### 评分投影（`ProductRatingProjection.accept`）

校验事件 → upsert 投影：仅当事件 revision 大于当前 revision 才覆盖（`GREATEST` 防乱序与重复）。

### 事件发布（`ProductSearchOutboxPublisher`）

每秒扫描待发布 → 同步发送（消息键 = eventId）→ 成功标记；失败按 `2^attempts` 指数退避（上限 300 秒）；每日清理已发布行。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 菜单缓存击穿（热窗口并发回源） | Redisson 窗口锁（wait 3s / lease 30s）+ 双重检查：抢到锁的线程回源并写缓存，其余线程等待后直接读缓存；未抢到锁退化为直接查库返回不写缓存 | 锁等待超时不阻塞请求；Redis 写失败只记日志 |
| 缓存与数据库不一致（写后读旧值） | 「先写库、后删缓存」+ 事务提交后（afterCommit）才执行缓存删除与布隆补写：删除动作绑定提交时机，杜绝回滚后误删、未提交就删导致旧值回填 | 删除失败只留日志，由 300 秒 TTL + 抖动兜底收敛 |
| 缓存穿透 | 窗口/菜品布隆过滤器拦截「一定不存在」的 ID；空值标记 60 秒挡住反复请求不存在窗口 | 布隆初始化失败不阻止启动（仅多一次回源）；误判只多一次查库，不影响正确性 |
| 缓存雪崩 | 菜单缓存 TTL 300 秒 + 0–120 秒随机抖动，避免同批键同时过期 | — |
| 管理端并发修改唯一名 | 唯一键（窗口内分类名、档口内菜品编码、启用规格组/选项名）+ 服务端 409 映射；规格替换以商品行锁串行化，旧配置软停用后重插 | 冲突返回「名称已存在」；替换原子，不残留半套规格 |
| 评分事件重复/乱序 | 投影按 revision 单调比较：仅事件 revision 大于当前才覆盖（SQL 内 `GREATEST` 防乱序与重复），CHECK 约束兜底数据自洽 | 重复/乱序事件被拒绝，重复消费幂等 |
| 搜索事件重复发布 | Outbox 唯一键 + 发布器标记 published_at；事件键 = eventId | 发布失败按 `2^attempts` 指数退避（上限 300 秒），每日清理已发布行 |
| 数据范围缓存与撤销 | 范围事实来自用户服务，短缓存 300 秒 | 查询失败不放行（fail-closed）；撤销最多 5 分钟延迟 |

## 接口清单

顾客端（CUSTOMER）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/products/restaurants`、`/products/windows` | 餐厅/窗口列表 |
| GET | `/products/windows/{windowId}/menu` | 窗口菜单（含规格） |
| GET | `/products/windows/{windowId}` | 单个窗口摘要（含取餐地点，历史订单回看用，不按营业状态过滤） |
| GET | `/products/images?productIds=` | 菜品图片（1–50） |
| GET | `/products/windows/{windowId}/stocks` | 窗口库存聚合 |
| GET | `/products/{productId}/rating` | 商品评分 |

管理端（`/products/admin/**`，STALL_MANAGER / RESTAURANT_MANAGER；餐厅管理另含 GENERAL_MANAGER）：窗口、分类、菜品的 CRUD，图片与图库替换，规格读取与完整替换，餐厅 CRUD；数据范围由服务层收敛。

内部接口（`X-Internal-Token`，网关不路由）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/products/quote` | 核价（随下单用户 JWT 调用） |
| GET | `/internal/products/search/**` | 搜索文档快照（窗口/整窗/单品） |
| GET/POST | `/internal/products/recommendations/**` | 推荐快照与候选复核 |
| GET | `/internal/windows/{windowId}`、`/internal/restaurants/{id}`、`/internal/windows/names` | 档口/餐厅查询与批量窗口名 |

业务失败统一 HTTP 200 + ApiResponse 业务码。

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 生产 | `product-search-refresh-v1`（Outbox，消费方搜索服务 + 推荐服务共用 topic） |
| 消费 | `product-rating-changed-v1`（评价服务生产，revision 单调幂等） |
| 缓存 | 活动窗口列表、窗口菜单（TTL 300s+抖动）、空值标记 60s、数据范围 300s |
| 布隆 | 窗口 1000 / 菜品 10000 预期，误判率 0.01；仅用于排除「一定不存在」；初始化失败不阻止启动 |

## 安全设计

JWT 验签 + Redis 黑名单 fail-closed；管理端角色门禁 + 数据范围（用户服务事实，查询失败不放行）；库存聚合失败统一服务不可用，不伪报零库存；Redis 写失败只记日志不影响业务。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `product.search-messaging.enabled / publish-delay-ms` | — / 1000 | 搜索事件开关与发布间隔 |
| `SENTINEL_*` / 降级规则 Data-ID | 127.0.0.1:9099 | Sentinel 看板与规则 |
| `internal.scope.token` | — | 共享令牌（与订单/用户一致） |
| `PRODUCT_DB_URL` / `REDIS_HOST/PORT` | 127.0.0.1:9095 / 9096 | 数据库与 Redis |
| `SWAGGER_ENABLED` | — | 文档开关 |

## 异常与失败行为

- 数据范围查询失败 fail-closed；唯一键冲突 409；越权 403。
- 缓存重建互斥、超时直接查库；布隆误判只多一次回源，不影响正确性。
- 规格替换以商品行锁串行化，旧配置软停用后重插，outbox 与缓存失效在提交后执行。

## 关键代码入口

`ProductQuoteServiceImpl`（核价与规格快照）→ `ProductMenuServiceImpl` + `ProductMenuCacheService`（多级缓存范式）→ `ProductAdministrationServiceImpl`（Outbox + afterCommit 缓存旁路）→ `ProductRatingProjection`（单调版本投影）。

## 规格与图库管理细则

- 配置上限：每菜品规格组最多 10 个、每组选项最多 20 个；加价非负、最多两位小数。
- 必选组必须包含至少一个零加价选项，保证菜品基础价始终是可售组合的最低起售价。
- 替换保存校验：组名同菜品唯一、选项名同组唯一（均限启用范围）；学生菜单的 specificationGroups 始终为数组，无规格菜品返回空数组。
- 图库编辑边界：已有图提升封面仅调顺序保留原图、新图替换首图、空串移除首图；普通资料更新仅在显式提交图片字段时才写图片列，避免无条件回写。
- 图片更新只修改图片列，不覆盖名称/价格等字段；窗口封面变更后失效窗口列表缓存，菜品图变更后失效所属窗口菜单缓存；菜单缓存键版本随图库功能升级。
- V11 图库迁移：回填旧封面到图库数组，CHECK 拒绝超 5 张或非数组；发布顺序为先迁移数据库、再更新商品服务、最后学生端。
- Outbox 保留策略：发布成功记录保留，每日 03:20 清理超过 30 天的已发布记录。
- 直接执行 SQL 修改商品不触发 Outbox 事件，依赖搜索服务每日对账补偿。
- 数据库账号 `product_service` 最小权限：仅授权本库增删改查，无权访问其他业务库。
- 核价契约补充：核价每次 ≤100 行、每行选项 ≤10；旧版仅传 productIds 的请求仍兼容。

## 已知限制与未验证项

- 仅支持单选规格；无多规格组合库存。
- 布隆过滤器容量为预估固定值，目录超预期后误判率上升。
- 评分投影仅存均分聚合，不存评分分布。

## 关联文档

- `docs/database/migrations/product_db/`：建表与变更脚本。
- `search-service/README.md`、`recommendation-service/README.md`：刷新事件消费方。
- `comment-service/README.md`：评分事件生产方。
- 根 README「跨模块业务链路」：菜单 → 加购 → 报价链路。
