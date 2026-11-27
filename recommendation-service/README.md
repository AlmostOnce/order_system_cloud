# recommendation-service 首页推荐服务

> 面向学生的首页可解释混合推荐服务：本地只读投影 + 规则打分（`rule-v1`），输出菜品、价格与推荐理由码。默认端口 `9112`，数据库 `recommendation_db`，消费订单/收藏/商品目录事件，定时回填历史并同步销量热榜。推荐数据全部落 MySQL，不进缓存。

## 模块定位与职责边界

- 只保存本地只读投影（购买快照、收藏状态、商品目录、搜索意图、热榜），不拥有任何业务事实。
- 不重算销量口径：热榜来自统计服务的内部接口，退款销量政策由统计服务独占。
- 交易与库存事实最终由商品/库存服务复核后才对外输出，投影滞后不会展示可能下架或缺货的旧快照。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server（jose）、Validation、Actuator |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Spring Cloud Stream RocketMQ |
| 数据 | MyBatis-Plus 3.5.8（注解 SQL）、Druid、MySQL `recommendation_db`（默认 `127.0.0.1:9095`） |
| 缓存 | Redis 用于 JWT jti 注销黑名单，以及定时任务互斥租约（`SET NX EX` + 比较删除 Lua）；无 Redisson |
| 定时任务 | `@EnableScheduling`，4 个 `@Scheduled` 任务，调度线程池 2 |

- 端口 `9112`；Feign 超时 connect 2000 / read 3000；`spring.profiles.include: recommendation-mq`。
- JWT：RSA 验签 + issuer 校验 + 黑名单前缀 `auth:access-token:blacklist:`；subject 经 `JwtUserIdentity.toInternalPrincipal` 换内部用户 ID。
- 服务间令牌：通用 `internal.scope.token`；订单快照回填使用独立的 `internal.order.token`。
- MQ 绑定：3 个消费 binding 均 `max-attempts:1`、`CONSUME_FROM_FIRST_OFFSET`、`max-reconsume-times:16`、非顺序消费。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `RecommendationController` | `GET /recommendations/home` 学生首页推荐 |
| `RecommendationInternalController` | `POST /internal/recommendations/search-intents`，接收搜索服务转发的搜索意图 |
| `RecommendationQueryServiceImpl` | 查询编排：本地投影排序 → 商品/库存批量复核 |
| `RecommendationAlgorithmImpl` | 纯计算打分器（无 IO）：内容相似、口味偏好、搜索意图、热度 |
| `RecommendationPurchaseProjectionServiceImpl` | 订单购买/退款快照投影（行锁 + 版本幂等 + 事务整单替换） |
| `RecommendationFavoriteProjectionServiceImpl` | 收藏当前态投影（Outbox 版本 upsert + 取消墓碑） |
| `RecommendationProductCatalogServiceImpl` / `RecommendationProductFeatureWriterImpl` | 从商品服务刷新目录快照并原子替换/撤架 |
| `RecommendationPopularityServiceImpl` / `RecommendationHotSalesWriterImpl` | 拉取统计服务榜单并事务整批替换 |
| `RecommendationSearchIntentServiceImpl` | 搜索意图幂等落库与 14 天清理 |
| `task/*` 4 个定时任务 | 历史回填、热榜刷新、目录对账、意图过期清理 |
| `RecommendationMessaging` | 3 个函数式 Consumer |
| `SpecificationPreferenceNormalizer` | 口味别名 → 受控标签映射（SPICE/SUGAR/TEMP/FLAVOR） |
| `client/*` 3 个自建 Feign + 复用 2 个 | 订单快照、收藏快照、统计榜单、商品目录/候选校验、批量库存 |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/recommendation_db/V1`、`V2`。

### recommendation_order_snapshots（订单头投影）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| order_id | BIGINT | NOT NULL | 主键 | 订单服务源订单主键 |
| user_id | BIGINT | NOT NULL | 用户支付历史索引参与 | 验签后解析出的内部用户主键 |
| window_id | BIGINT | NOT NULL | — | 下单窗口 |
| order_version | INT | NOT NULL | — | 源订单单调版本，用于拒绝重复与乱序快照 |
| paid_at | DATETIME | NOT NULL | 用户支付历史索引参与 | 首次支付成功时间，退款不改变该时间 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 投影更新时间 |

- 索引：`idx_recommendation_order_user_paid(user_id, paid_at DESC, order_id)`。

### recommendation_order_items（订单明细投影）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| item_id | BIGINT | NOT NULL | 主键 | 源订单明细主键，部分退款定位到该行 |
| order_id | BIGINT | NOT NULL | 订单索引参与 | 所属源订单主键 |
| product_id | BIGINT | NOT NULL | 订单/商品索引参与 | 推荐粒度为菜品，不按规格拆分 |
| selection_key | VARCHAR(256) | NOT NULL，默认 'base' | — | 下单时规格选项组合键 |
| specification_text_snapshot | VARCHAR(1024) | NOT NULL，默认 '' | — | 下单时规格显示文字 |
| specification_selection_snapshot | JSON | 可空 | — | 下单时服务端确认的结构化组/选项快照 |
| purchased_quantity | INT | NOT NULL | — | 支付订单该明细原购买份数 |
| refunded_quantity | INT | NOT NULL，默认 0 | — | 资金确认成功且已撤销的该行份数 |

- 索引：`idx_recommendation_items_order(order_id, product_id)`、`idx_recommendation_items_product(product_id)`。

### recommendation_favorite_states（收藏当前态投影）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| user_id | BIGINT | NOT NULL | 联合主键；用户收藏索引参与 | 内部用户主键 |
| target_type | VARCHAR(16) | NOT NULL | 联合主键；用户收藏索引参与 | PRODUCT 或 WINDOW |
| target_id | BIGINT | NOT NULL | 联合主键 | 菜品或窗口主键 |
| source_version | BIGINT | NOT NULL | — | 收藏 Outbox 自增 ID 数值版本，不能按字符串比较 |
| active | TINYINT(1) | NOT NULL | 用户收藏索引参与 | 当前是否收藏；取消墓碑也保留以压过迟到旧事件 |
| changed_at | DATETIME | NOT NULL | — | 源收藏状态变更时间 |

- 联合主键 `(user_id, target_type, target_id)`；索引 `idx_recommendation_favorite_user(user_id, active, target_type)`。

### recommendation_search_intents（搜索意图）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| intent_id | BIGINT | NOT NULL，自增 | 主键 | 搜索意图本地编号 |
| user_id | BIGINT | NOT NULL | uk_recommendation_search_query；用户时间索引参与 | 搜索侧解析的内部用户主键 |
| query_id | VARCHAR(64) | NOT NULL | uk_recommendation_search_query | 一次主动首屏搜索的幂等标识 |
| keyword | VARCHAR(80) | NOT NULL | — | 短期保留的搜索关键词，不写入普通日志 |
| restaurant_id / window_id / category_id | BIGINT | 各自可空 | — | 搜索时指定的范围过滤 |
| ranked_product_ids | JSON | NOT NULL | — | 成功首屏结果菜品 ID 按检索顺序，最多 20 项 |
| created_at | DATETIME | NOT NULL，默认当前时间 | 用户时间索引参与 | 搜索成功时间，14 天保留与时间衰减依据 |

- 唯一键：`uk_recommendation_search_query(user_id, query_id)`；索引 `idx_recommendation_search_user_time(user_id, created_at DESC, intent_id)`。

### recommendation_product_features（商品内容特征投影）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| product_id | BIGINT | NOT NULL | 主键 | 商品服务菜品主键 |
| category_id | BIGINT | 可空 | 分类候选索引参与 | 商品分类主键 |
| window_id | BIGINT | NOT NULL | 窗口/分类索引参与 | 所属窗口主键 |
| restaurant_id | BIGINT | NOT NULL | 餐厅索引参与 | 所属餐厅主键 |
| window_name | VARCHAR(100) | NOT NULL，默认 '' | — | 窗口展示名称快照 |
| category_name | VARCHAR(100) | 可空 | — | 分类展示名称快照 |
| product_name | VARCHAR(100) | NOT NULL | — | 菜名文本特征 |
| description | VARCHAR(1000) | 可空 | — | 菜品描述文本特征 |
| image_url | VARCHAR(512) | 可空 | — | 当前展示图片地址 |
| base_price | DECIMAL(10,2) | NOT NULL | — | 当前基础售价，不代表已选规格最终报价 |
| sort_order | INT | NOT NULL，默认 0 | 候选索引参与 | 目录稳定顺序，同分确定性排序 |
| has_specifications | TINYINT(1) | NOT NULL，默认 0 | — | 当前是否有启用规格组 |
| specification_groups | JSON | 可空 | — | 当前可选规格，仅用于明确口味匹配 |
| available | TINYINT(1) | NOT NULL，默认 1 | 候选索引参与 | 目录状态；下架快照保留作历史画像特征 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 目录快照更新时间 |

- 索引：`idx_recommendation_product_window(window_id, available, sort_order, product_id)`、`idx_recommendation_product_restaurant(restaurant_id, available, sort_order, product_id)`、`idx_recommendation_product_category(window_id, category_id, available)`。

### recommendation_hot_products（热榜快照）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| product_id | BIGINT | NOT NULL | 主键 | 统计服务榜单菜品主键 |
| window_id | BIGINT | NOT NULL | — | 榜单窗口主键 |
| sales | BIGINT | NOT NULL | — | 统计服务定义的累计已支付份数 |
| rank_no | INT | NOT NULL | 榜单索引参与 | 统计服务当前排名 |
| updated_at | DATETIME | NOT NULL，默认当前时间 | — | 最近一次成功读取榜单时间 |

- 索引：`idx_recommendation_hot_rank(rank_no, product_id)`。

### recommendation_source_cursors（回填断点）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| source_name | VARCHAR(32) | NOT NULL | 主键 | 固定源名称：purchase-orders 或 favorite-relations |
| cursor_value | BIGINT | NOT NULL，默认 0 | — | 最后完整投影的源主键，按数值递增且不回退 |
| upper_bound | BIGINT | 可空，默认 NULL | — | 收藏首屏捕获的 favorite_id 上界；订单源为空 |
| completed | TINYINT(1) | NOT NULL，默认 0 | — | 首轮历史扫描是否到达末页；之后变化由实时事件补齐 |
| updated_at | DATETIME | NOT NULL，自动更新 | — | 最近一次成功推进断点时间 |

## 核心业务流程

### 事件投影（三个消费 binding）

- 订单快照 `recommendation-purchase-snapshot-v1`（订单服务 Outbox 生产）：校验事件与规格快照一致性 → 插入锁行 + `SELECT ... FOR UPDATE` 行锁 → `order_version` 旧版本直接丢弃（幂等）→ 事务内更新订单头、删除并重插全部明细。
- 收藏变更 `user-favorite-changed-v1`（用户服务 Outbox 生产）：从 eventId 解析版本号，`upsertIfNewer` 以版本单调比较防迟到旧事件复活已取消的收藏。
- 商品目录刷新 `product-search-refresh-v1`（与搜索服务共用 topic）：拉取商品快照后事务内 upsert，并将本窗口缺失的商品标记撤架；整批成功才生效，失败保留旧投影。

### 历史回填（`RecommendationSourceBackfillTask`）

延迟 15 秒启动、每 60 秒一轮，每轮 ≤10 页 ×100 条：订单按 ID 游标、收藏按游标 + 水位线分页拉取并投影；整页成功才推进游标，失败保留断点下轮重读，不丢不重。

### 热榜同步（`RecommendationPopularityRefreshTask`）

每 60 秒从统计服务内部接口拉 Top50，事务内整批替换热榜表；替换失败回滚保留旧榜。

### 首页查询与推荐算法（`RecommendationQueryServiceImpl.recommend` → `RecommendationAlgorithmImpl.rank`，规则版 `rule-v1`）

#### 输入与有界处理

| 输入 | 上限 | 来源 |
|---|---|---|
| 候选集（可用商品目录） | ≤2000 | 本地投影表 |
| 历史商品特征（含已撤架） | 与候选合并去重 | 本地投影表 |
| 近 90 天订单头与明细 | ≤500 | 本地投影表 |
| 收藏状态 | 无上限（本人全部） | 本地投影表 |
| 近 14 天搜索意图 | ≤20，打分只用最近 5 条 | 本地投影表 |
| 热榜 | ≤50 | 本地热榜快照 |

超过上限直接抛「有界处理上限」异常；候选为空立即返回 `COLD_START` 空结果。所有打分在服务本地完成，请求路径上不逐菜调用外部服务。

#### 第一步：购买兴趣画像（明细 → 订单/菜品合并）

1. 明细合法性核验：购买份数 >0、退款份数 ∈ [0, 购买份数]，违反约束抛异常（不猜测）。
2. 退款核减：菜品净份数 = 购买 − 退款，净量 ≤0 的菜品不计入兴趣；退款只撤销对应口味选择（按行净量分摊）。
3. 时间衰减：订单权重 = `2^(-距支付天数/30)`（半衰期 30 天；缺失或未来时间戳分别按已衰减/年龄 0 处理，防时钟偏差超 1）。
4. 单单封顶：每订单每菜品净份数按 3 封顶（防一次狂买刷爆画像），多规格行先合并总净量再按行净量比例分摊封顶额度。
5. 品类兴趣分（itemInterest）：`min(1, log(1+Σ衰减净量)/log 6)` 归一后与收藏按 **6:4** 混合——`itemInterest = (6×购买分 + 4×收藏分)/10`。

#### 第二步：内容向量与用户画像（TF-IDF 余弦相似）

- 文档向量构成：菜名（权重 3.0）、分类名（1.5）、描述（0.5）、`WINDOW:{windowId}`（1.5）、`CATEGORY:{windowId}:{categoryId}`（1.0）；**可选项不进向量**，避免把「可选规格」误作实际口味。
- 分词：中文按字符二元组（如「麻辣香锅」→ 麻辣/辣香/香锅），拉丁文本按 NFKC 小写词项；标点与空白归一化。
- TF-IDF：`词频 × (log((文档数+1)/(包含该词的文档数+1)) + 1)`，再对每个向量 L2 归一化。
- 用户画像向量：本人有效商品（购买或收藏）的特征向量按品类兴趣分加权求和后 L2 归一化；零画像安全返回空向量。
- 内容相似度 = 画像向量与候选向量在 [0,1] 内截断的余弦值；任一方向为空向量记 0 分，不产生 NaN。

#### 第三步：口味偏好（结构化选择快照，绝不猜测）

- 标签来源：只从**下单时保存的结构化规格选择快照**提取，绝不从可选项或自由文本猜测。
- 归一规则（`SpecificationPreferenceNormalizer`）：先按组名定语义（含「辣」→辣度；含「糖/甜」→糖度；含「温/冰」→温度；明确口味组→风味），再匹配受控别名表映射为 `SPICE:NONE~HOT`、`SUGAR:NONE~REGULAR`、`TEMP:HOT~MORE_ICE`、`FLAVOR:*` 等 4 维受控标签；未收录的组名或选项一律忽略。
- 口味权重：按行净量×衰减×封顶分摊累计；口味匹配分 = 候选可选标签命中的偏好强度占偏好总强度比例，未命中不算「讨厌」。
- 有口味画像时，内容分融合为 `0.9×内容相似 + 0.1×口味匹配`。

#### 第四步：搜索意图分（折扣倒数排名）

- 取最近 5 条成功搜索（≤20 条加载），按 3 天半衰期衰减（短期信号）。
- 每条结果按首屏排名给折扣倒数分：`查询权重 / log2(排名+1)`，排名越靠前分越高。
- 全部查询权重求和后归一并截断到 [0,1]；分页与重试已由 queryId 幂等入口合并，不会重复计分。
- 指定餐厅查询时忽略其他餐厅的搜索意图。

#### 第五步：热度分与动态加权合成

- 热度分 = `log1p(销量)/log1p(最高销量)`（对数尺度，防头部商品碾压）。
- 五路信号**动态启用**：无画像/无搜索/无收藏窗口/无热度时对应权重直接置 0，剩余权重重新归一：

| 信号 | 权重 | 启用条件 |
|---|---|---|
| 内容相似（含口味融合） | 0.35 | 画像或口味权重非空 |
| 品类兴趣 | 0.20 | 任一商品兴趣分 >0 |
| 搜索意图 | 0.25 | 任一搜索分 >0 |
| 窗口收藏 | 0.05 | 候选窗口在收藏中 |
| 热度 | 0.15 | 候选在热榜且有销量 |

- 近 2 小时购买过的商品总分 ×0.5（避免刚买过的菜霸榜）。

#### 第六步：排序、打散与多样性

- 排序键：总分降序 → tieKey 升序 → 菜品 ID 升序；tieKey = `hash(用户ID:菜品ID:两小时时间桶)`——对同一用户同一时间窗稳定、跨两小时轮换，让同分候选自然换位实现探索。
- 多样性（`diversify`）：先按「每窗口最多 2 道」选取；不足 limit 时逐轮放宽窗口配额（2→3→…→limit）；候选总数不足时少返回而不复制商品。

#### 理由码（只给结论，不暴露评分与原始搜索词）

优先级从高到低：`PURCHASED`（买过且非收藏）→ `FAVORITED` → `FLAVOR_MATCH`（口味命中）→ `SEARCH_RELATED`（搜索分 >0）→ `WINDOW_FAVORITE` → `CONTENT_SIMILAR`（内容分 >0）→ `POPULAR`（热榜命中）→ `EXPLORE`（无明确理由，由 tieKey 探索）。

#### 输出模式与实时复核

1. 打分排序后取前 50 项：实时调商品服务复核候选有效性、调库存服务批量查库存，剔除不可售候选。
2. 复核通过返回 `mode=PERSONALIZED` 或 `COLD_START`（无任何个性化信号时按热榜+目录兜底排序）；商品或库存复核失败返回 `mode=FALLBACK` 空列表，绝不展示可能下架或缺货的旧快照。

### 搜索意图接收

搜索服务按内部令牌转发搜索记录：按 queryId 唯一键幂等落库，10 分钟去重窗口，每日每人 10 条上限，14 天过期清理。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 订单快照重复/乱序投递 | 插入锁行 + 订单头 `FOR UPDATE` 行锁串行化同订单版本；`order_version` 单调比较，旧版本直接丢弃 | 幂等命中返回成功，不重放投影 |
| 收藏事件迟到/复活 | `source_version`（收藏 Outbox 自增 ID）数值单调比较 upsert（`upsertIfNewer`）；取消也保留墓碑行 | 旧版本事件被拒绝，取消状态不被旧新增覆盖 |
| 回填断点回退 | 游标 `GREATEST` 只增不减，completed 单向置 1；整页成功才推进，失败保留断点下轮重读 | 不丢不重；并行实例间游标不会倒退 |
| 搜索意图重复提交 | 唯一键 `(user_id, query_id)` + INSERT IGNORE 幂等；10 分钟去重窗口，每日每人 10 条上限 | 重复意图被吸收（注：计数与插入非原子，双实例并发时每日上限可能略超） |
| 商品/热榜替换的原子性 | 目录刷新与热榜替换均为事务整批（先删改后写入），失败回滚保留旧投影 | 不出现半新半旧目录 |
| 回填与实时投影竞争 | 回填走与实时相同的投影方法（行锁 + 版本幂等），实时事件晚于回填时按版本覆盖 | 结果收敛到最新版本 |
| 推荐结果与真实状态漂移 | 输出前实时调商品/库存服务复核候选，复核失败返回 FALLBACK 空列表 | 绝不展示可能下架或缺货的旧快照 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/recommendations/home?restaurantId=&limit=` | JWT + CUSTOMER | limit 1-10 默认 10；返回推荐批次号、算法版本、模式与条目（商品/窗口/价格类型 FIXED|STARTING/理由码） |
| POST | `/internal/recommendations/search-intents` | X-Internal-Token | 搜索意图落库，网关不路由 |

匿名放行：`/actuator/health`、`/v3/api-docs/**`、`/swagger-ui/**`、`/internal/**`（内部令牌另行校验）。

## 消息、异步与缓存设计

| binding | topic | 消费组 | 生产方 |
|---|---|---|---|
| orderRecommendationSnapshot-in-0 | `recommendation-purchase-snapshot-v1` | 同名 | 订单服务 Outbox |
| favoriteChanged-in-0 | `user-favorite-changed-v1` | `recommendation-favorite-state-v1` | 用户服务 Outbox |
| productSearchRefresh-in-0 | `product-search-refresh-v1` | `recommendation-product-refresh-v1` | 商品服务 Outbox（与搜索服务共用） |

- 幂等：消息体内版本号（`orderVersion`/`sourceVersion`/eventId）+ 数据库唯一键 + 行锁。
- 错误处理：本地重试 1 次即抛回 Binder，RocketMQ 重投 16 次后进死信；`CONSUME_FROM_FIRST_OFFSET` 保证重启不丢，回填游标兜底。
- 本服务不生产任何消息；推荐数据不进 Redis，全部以 MySQL 为准。

## 安全设计

与全系统资源服务一致：RSA JWT 验签 + issuer + jti 黑名单（黑名单不可查时拒绝）；学生角色限定；内部接口共享令牌 + 独立订单回填令牌。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `recommendation.backfill.initial-delay-ms / delay-ms` | 15000 / 60000 | 历史回填节奏 |
| `recommendation.catalog.refresh-delay-ms / request-timeout-ms` | 300000 / 3000 | 目录对账节奏与上游超时 |
| `RECOMMENDATION_HOT_INITIAL_DELAY_MS / REFRESH_DELAY_MS` | 15000 / 60000 | 热榜刷新节奏 |
| `internal.scope.token` / `internal.order.token` | — | 服务间共享令牌、订单快照专用令牌 |
| `ROCKETMQ_NAMESERVER` | 127.0.0.1:9876 | MQ 地址 |
| `SWAGGER_ENABLED` | — | 接口文档开关 |

## 异常与失败行为

- 数据依赖（订单/收藏/目录读取）失败向上抛出，不伪装成「无行为」；商品或库存复核失败返回 FALLBACK 空列表。
- 上游快照失败保留旧投影：目录替换、热榜替换均为事务整批，失败回滚。
- 回填任务捕获全部异常仅记日志，下一轮从断点续跑。
- 并发控制：订单投影行锁串行同订单版本；搜索意图 INSERT IGNORE；游标 `GREATEST` 只增不减；目录对账与热榜刷新用 Redis 租约（`ScheduledTaskLock`）保证多实例同轮只执行一次，其余投影靠唯一键与行锁。
- 已知限制：搜索意图的计数与插入非原子，双实例并发时每日上限可能略超 10 条。

## 关键代码入口

`RecommendationQueryServiceImpl`（查询编排与降级边界）→ `RecommendationAlgorithmImpl`（打分与理由码）→ `RecommendationPurchaseProjectionServiceImpl`（投影幂等范式）→ `task/RecommendationSourceBackfillTask`（断点回填）。

## 服务间快照契约与后续规划

- 内部快照上限：订单快照一次最多 100 单且订单头与明细在同一 REPEATABLE_READ 快照读内获取；收藏快照最多 100 项；推荐候选复核的库存批量一次最多 50 个 (windowId, productId) 组合。
- 规格选择键生成规则：无选择为 `base`，有选择为选项 ID 升序短横线连接。
- 搜索意图采集资源约束：专用线程池 2 线程 / 64 等待队列，队列满或远程失败即丢弃弱信号，不阻塞搜索。
- 曝光与点击采集未实现；后续加入时应按推荐批次号去重并关联同用户同菜支付，首期点击不计入权重。
- 后续 ItemCF 设计要点：余弦相似度、≥3 名不同用户共同支持、`support/(support+10)` 小样本收缩、每菜保留 Top20 邻居、每用户最多 100 个有效历史商品、离线定时计算。
- 离线评估方案：按时间切分数据集评估 HitRate@10 / Recall@10 / NDCG@10，记录覆盖率、店铺分布、长尾曝光与延迟，禁止未来数据泄漏。
- 与商品规格规则联动：加价非负且每个必选组至少一个零加价选项，因此菜品基础价可作为可售组合的最低起售价。

## 已知限制与未验证项

- 算法为规则版 `rule-v1`，无模型训练与在线学习。
- 重建/对账依赖分页拉取，商品目录规模增长后候选查询与复核耗时需关注。
- 运行联调与真机验收未完成，投影正确性以回填对账结果为准。

## 关联文档

- `docs/database/migrations/recommendation_db/`：建表与变更脚本。
- `statistics-service/README.md`：热榜口径来源。
- 根 README「跨模块业务链路」：推荐数据链路全景。
