# search-service 搜索服务

> 菜品全文搜索（Elasticsearch 只读入口）与搜索索引维护（事件增量同步、全量重建、每日对账）。默认端口 `9106`，ES 索引别名 `menu-products-current`；商品主库（MySQL）才是事实来源，ES 只存快照。消费商品服务的搜索刷新事件，并向推荐服务转发搜索意图弱信号。

## 模块定位与职责边界

- 对外提供菜品关键词搜索与条件过滤；对内维护搜索索引的增量刷新、影子索引重建与对账修复。
- 任何刷新/重建都通过 Feign 从商品服务内部接口拉取当前快照，绝不自行推算商品数据。
- 搜索意图是弱信号：搜索成功首屏后异步把查询记录转发给推荐服务；采集失败只丢弃，不影响搜索可用性与结果排名。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Validation / Actuator / Security / OAuth2 Resource Server（jose） |
| 检索 | Spring Data Elasticsearch（ES `http://127.0.0.1:9200`） |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer、Spring Cloud Stream RocketMQ |
| 并发 | Redisson（Redisson 读写锁 + 看门狗） |
| 共享模块 | common-web、common-redis、product-api |

- `spring.profiles.include: search-mq`；消费 binding `productSearchRefresh-in-0` → topic `product-search-refresh-v1`，消费组 `product-search-index-v1`；`max-attempts:1` + 显式重抛错误处理（交 Broker 重试）、`CONSUME_FROM_FIRST_OFFSET`、非顺序消费、Broker 重投 16 次。
- 意图转发 Feign：connect 500ms / read 1500ms，日志级别 none（防止搜索词进入日志）。
- 身份回查 `identity.user-service-base-url`（默认 :9092）；共享令牌 `internal.scope.token`。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `ProductSearchController` | 顾客搜索入口（CUSTOMER） |
| `SearchInternalController` | 内部全量重建入口（`X-Internal-Token`） |
| `ProductSearchServiceImpl` | 查询、整窗/单品增量刷新、全量重建、对账修复 |
| `SearchIntentRecorderImpl` | 成功首屏的有界异步意图采集 |
| `RecommendationSearchIntentClient` | Feign → 推荐服务搜索意图接口 |
| `ProductSearchRefreshConsumer` | 事件消费入口与错误处理 |
| `MenuProductDocument` | ES 文档映射 |
| `SearchIndexManager` | 别名与物理索引管理（建索引/原子切别名） |
| `SearchIndexLock` | Redisson 读写锁 + 窗口级互斥锁 |
| `SearchIndexBootstrap` | 启动后空索引自动重建（失败保留重试） |
| `SearchIndexReconcileTask` | 每日对账调度 |
| `SearchIndexInitializer` | 启动时建索引/接别名 |
| `SearchIntentAsyncConfig` | 专用有界线程池（2 线程 / 64 队列 / Abort 策略） |
| `SearchSecurityConfig` 及错误处理器 | JWT 资源服务器 + fail-closed 黑名单 + 统一 401/403 |

## 数据模型（Elasticsearch）

- 别名 `menu-products-current` 指向物理索引（形如 `menu-products-<UUID>`），1 shard / 0 replica；旧版本索引保留以便恢复。
- 文档 `id = windowId:productId`；名称/描述/窗口/餐厅/分类为 cjk 分词文本；菜名为 search_as_you_type（支持前缀与 n-gram 匹配）；餐厅位置、图片地址、价格为 keyword/数值。
- 本服务无 MySQL 表；刷新事件由商品服务的 Outbox 表与发布器产出（同库同事务写入，延迟发布，同步发送）。

## 核心业务流程

### 搜索与意图转发

1. 搜索：`bool_prefix` 多字段匹配（菜名及其 n-gram、描述、分类、窗口、餐厅）+ 餐厅/窗口/分类 term 过滤，分页返回。
2. 第一页且带查询标识时记录意图：后台线程校验查询 ID 格式与长度 → 截取前 20 个结果商品 ID → 解析内部用户 ID → 转发推荐服务；任何失败仅记日志类别，不重试、不阻塞搜索。

### 事件增量同步

- 消费 `product-search-refresh-v1`：事件校验失败抛异常交 Broker 重投。
- 整窗刷新（productId 为空）：读锁 + 窗口锁 → 拉全窗快照 → 按窗口删除后逐条写入；重复消费结果相同（幂等）。
- 单品刷新：拉单品快照，空则删除文档、有则写入；快照与事件不匹配抛异常重投。

### 全量重建与对账

- 重建：写锁 → 新建影子索引 → 逐窗拉快照写入 → refresh + count 比对（不一致抛异常）→ 原子切换别名。
- 启动同步：索引无文档时自动触发重建，失败按间隔重试。
- 每日对账（3:30）：读锁下构建商品库期望集，流式遍历 ES（每页 500）按 13 个字段全等比对；缺失、不一致、重复、缺 ID 逐个修复，修复时重读商品当前状态避免覆盖并发变更。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 同窗口并发刷新 | Redisson 窗口级互斥锁 `search:menu:reindex:{windowId}`：同一窗口的刷新串行化，重复消费结果相同（按主库快照整体替换） | 锁等待超时返回「索引正在切换，请稍后重试」 |
| 刷新与重建并发 | Redisson 读写锁：增量刷新持读锁、全量重建/对账持写锁；重建用影子索引 + 别名原子切换，不影响读 | 读锁排队等待；看门狗防长任务锁过期 |
| 重建中途失败 | 影子索引写入后 refresh + count 比对，不一致抛异常不切别名；旧版本索引保留以便恢复 | 别名仍指向旧索引，搜索不中断 |
| 单品快照与事件错序 | 刷新时重读商品服务当前快照，事件载荷仅作触发；快照与事件不匹配抛异常交 Broker 重投 | 乱序事件不会写入陈旧数据 |
| 对账与并发变更竞争 | 每日对账读锁下比对商品库期望集与 ES（流式每页 500）；修复时重读商品当前状态再写，避免覆盖并发变更 | 比对出的缺失/不一致/重复逐个修复，失败留待下轮 |
| 意图采集并发堆积 | 专用有界线程池（2 线程 / 64 队列 / Abort）：队列满即丢弃，不回压搜索线程 | 采集允许丢失，只记日志类别，不影响搜索可用性与排名 |
| 重复消费 | 按主库快照整体替换，重复消费结果幂等；无独立去重表 | 幂等由替换语义保证 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/search/products` | CUSTOMER | 关键词（≤80 字符）+ 餐厅/窗口/分类过滤 + 分页（size ≤100）；可带查询标识用于意图采集 |
| POST | `/internal/search/rebuild` | X-Internal-Token | 全量重建，返回窗口数 |
| GET | `/actuator/health`、swagger | 匿名 | 健康与文档 |

安全过滤链：`/search/**` 需认证，`/internal/**` 由令牌校验，其余 `denyAll`。

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 消费 | `product-search-refresh-v1`，消费组 `product-search-index-v1` |
| 生产方 | 商品服务 Outbox（延迟发布、同步发送） |
| 幂等 | 按主库快照整体替换，重复消费结果相同 |
| 死信 | 无独立死信处理，依赖 Broker 重投 16 次后进 DLQ 与每日对账兜底 |
| 缓存 | 无结果缓存；Redisson 锁键 `search:menu:index-switch` 与 `search:menu:reindex:{windowId}` |

## 安全设计

JWT 验签 + 黑名单 fail-closed；意图转发使用共享令牌且日志级别 none，搜索词不落日志。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `search.bootstrap.initial-delay-ms / retry-delay-ms` | 5000 / 60000 | 启动同步节奏 |
| `search.reconcile.cron` | `0 30 3 * * *` | 每日对账 |
| `ROCKETMQ_NAMESERVER` | 127.0.0.1:9876 | MQ 地址 |
| `internal.scope.token` | — | 服务间共享令牌 |
| `identity.user-service-base-url` | :9092 | 身份回查地址 |

## 异常与失败行为

- 意图采集允许丢失：专用线程池队列满即丢弃（禁止回压到搜索线程），应用停止不排空队列。
- 商品源数据不可用：消费事件抛异常交 Broker 重试；调度任务记日志等下轮。
- ES 查询失败不采集意图、不伪造空结果；锁等待超时返回「搜索索引正在切换，请稍后重试」；Redisson 看门狗防长任务锁过期。

## 关键代码入口

`ProductSearchServiceImpl`（刷新/重建/对账三种同步范式）→ `SearchIndexManager`（别名原子切换）→ `SearchIntentRecorderImpl`（有界异步弱信号）。

## 升级与配套约定

- 升级顺序约束：先停所有旧版搜索消费者 → 部署能同时识别新旧消息的消费者 → 最后部署商品生产者；旧消费者写旧物理索引，新旧版本不能混跑。
- 只有窗口 ID 的旧版消息仍按整窗刷新（向后兼容）。
- 直接修改 MySQL 不产生 Outbox 事件，该部分漂移由每日对账补偿。
- Elasticsearch 由 Docker Compose `search` profile 启动，默认地址 `http://127.0.0.1:9200`。
- 商品与搜索两端的 `ROCKETMQ_NAMESERVER`、`INTERNAL_SCOPE_TOKEN` 必须取值一致。

## 已知限制与未验证项

- 事件消费无已消费去重表，依赖快照替换的天然幂等。
- 重建为全量拉取，商品规模增长后重建时长与源服务压力需关注。
- 索引一致性依赖每日对账，日内的短暂不一致是设计内行为。

## 关联文档

- `product-service/README.md`：搜索刷新 Outbox 的生产侧。
- `recommendation-service/README.md`：搜索意图的消费与打分用途。
- 根 README「跨模块业务链路」：商品变更 → 索引同步链路。
