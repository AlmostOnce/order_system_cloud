# comment-service 评价服务

> 已完成订单的「菜品评价」与「窗口评价」服务：评价元数据与评分聚合存 MySQL（`comment_db`），评价正文/图片/标签存 MongoDB；评分变更经本地消息表发布事件供商品服务投影。默认端口 `9105`。无删除功能，无商家回复功能。

## 模块定位与职责边界

- 只负责评价内容、评分聚合与评分事件发布；订单事实经订单服务实时校验，图片归属经文件服务校验，头像经用户服务回源。
- 跨库两阶段提交：Mongo 正文先写（未提交态），MySQL 元数据与评分提交后再标记可见；对账任务清理孤儿文档。
- 窗口评价为纯 MySQL 单库实现，不经过 Mongo。

## 技术栈与运行参数

| 类别 | 内容 |
|---|---|
| 框架 | Spring Boot Web / Security / OAuth2 Resource Server、Validation、Actuator |
| 数据 | MyBatis-Plus 3.5.8 + Druid + MySQL `comment_db`（默认 `127.0.0.1:9095`）、MongoDB `comment_db`（27017） |
| 缓存 | Redis：评分读模型短缓存 |
| 消息 | Spring Cloud Stream RocketMQ（仅生产） |
| 微服务 | Nacos Discovery、OpenFeign + LoadBalancer |
| 共享模块 | common-web、common-redis、order-api、comment-api |

- Feign 订单客户端 connect 3000 / read 5000，透传用户 Bearer；定时任务线程池 2。
- MQ 生产 binding `commentRating-out-0` → topic `product-rating-changed-v1`，生产组 `comment-rating-outbox-v1`，同步发送。
- `comment-api` 为纯契约包（`ProductRatingChangedEvent`、`ProductRatingVO`），供商品服务消费评分事件时使用。

## 代码结构与分层

| 类 | 职责 |
|---|---|
| `CommentServiceImpl` | 菜品评价核心：幂等、跨库两阶段、分页、评分汇总 |
| `CommentMetadataWriterImpl` | MySQL 元数据 + 评分累加 + Outbox 同事务提交 |
| `PurchaseVerifierImpl` | 经订单服务校验订单已完成、本人、含该菜品，返回窗口 ID |
| `WindowReviewServiceImpl` | 窗口评价（单库事务） |
| `RatingSummaryServiceImpl` | 评分读模型 Redis 短缓存（key `comment:rating:v1:{productId}`，TTL 随机 30–60 秒防雪崩） |
| `RatingBatchServiceImpl` | 窗口/商品批量评分（≤100 个 ID） |
| `OrderReviewServiceImpl` | 按订单聚合本人菜品评价与店铺评价 |
| `AuthorAvatarServiceImpl` | 批量（≤50）回源用户服务头像，失败降级为默认头像 |
| `RatingEventPublisher` | 定时扫描 Outbox 发事件 + 指数退避重试 + 定期重投最新快照 |
| `CommentOrphanCleaner` | 定时对账 24 小时前仍未提交的 Mongo 文档 |
| `CommentContentRepository` | Mongo 访问层（插入、按状态标记提交、孤儿查询） |
| `CommentMapper` / `CommentWriteMapper` / `WindowReviewMapper` | 元数据读写、评分累加、Outbox 写入 |
| `CommentSecurityConfig` 及错误处理器 | JWT + 黑名单 fail-closed + 统一 401/403 |

## 领域模型与数据表

迁移脚本：`docs/database/migrations/comment_db/`。

### comments（评价元数据，正文/图片/标签存 MongoDB）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT UNSIGNED | NOT NULL，自增 | 主键 | 内部排序主键，不向客户端暴露 |
| comment_id | CHAR(36) | NOT NULL | 唯一键 uk_comment_uuid | 对外评价 UUID，与 MongoDB 文档标识一致 |
| user_id | BIGINT UNSIGNED | NOT NULL | uk_comment_request；idx_comment_user 等参与 | 内部评价用户 ID，由认证主体确定 |
| author_name | VARCHAR(64) | NOT NULL | — | 评价时的用户昵称快照 |
| order_id | BIGINT UNSIGNED | NOT NULL | uk_comment_purchase | 已完成的购买订单 ID |
| product_id | BIGINT UNSIGNED | NOT NULL | uk_comment_purchase；idx_comment_product 等参与 | 该订单实际购买的菜品 ID |
| window_id | BIGINT UNSIGNED | NOT NULL | — | 订单所属窗口 ID，来自订单服务 |
| score | TINYINT UNSIGNED | NOT NULL | 查询索引参与列 | 评分 1–5 星（CHECK 限 1–5） |
| status | TINYINT | NOT NULL，默认 1 | CHECK 恒等于 1 | 可见状态，首期 1 为已发布 |
| request_key | CHAR(36) | NOT NULL | uk_comment_request | 客户端稳定幂等请求 UUID |
| request_hash | CHAR(64) | NOT NULL | — | 归一化请求 SHA-256 摘要，识别同键不同内容 |
| has_images | TINYINT | 可空，默认 NULL | 带图筛选索引参与列 | 是否带图：NULL 待回填、0 无图、1 有图 |
| created_at | DATETIME(6) | NOT NULL | — | 评价提交时间 |

- 唯一键：`uk_comment_uuid(comment_id)`、`uk_comment_purchase(order_id, product_id)`（一单一菜一条）、`uk_comment_request(user_id, request_key)`（幂等）。
- 普通索引：商品/用户两组「基础、按评分、按带图」共 6 个分页筛选索引（V1/V3/V4 渐进添加）。
- CHECK：`ck_comment_score`（1–5）、`ck_comment_status`（=1）。

### comment_rating_stats（菜品评分权威汇总）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| product_id | BIGINT UNSIGNED | NOT NULL | 主键 | 菜品 ID |
| review_count | BIGINT | NOT NULL | — | 已发布评价数量 |
| score_total | BIGINT | NOT NULL | — | 已发布评分总和 |
| revision | BIGINT | NOT NULL | — | 本菜品评分快照递增版本，事件发布与投影幂等依据 |

- CHECK：`ck_comment_rating`（评价数 >0 且 分数总和 ≥ 评价数且 ≤ 评价数×5 且 revision >0）。

### comment_rating_outbox（评分事件可靠投递）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| event_id | CHAR(36) | NOT NULL | 主键 | 评分事件 UUID |
| product_id | BIGINT UNSIGNED | NOT NULL | uk_comment_rating_version | 菜品 ID |
| revision | BIGINT | NOT NULL | uk_comment_rating_version | 对应权威评分版本 |
| payload | JSON | NOT NULL | — | 全量评分事件快照，不含用户与正文 |
| attempts | INT | NOT NULL，默认 0 | — | 发布失败次数 |
| next_attempt_at | DATETIME | NOT NULL，默认当前时间 | 待发布扫描索引参与列 | 下一次允许发送时间 |
| published_at | DATETIME | 可空 | 待发布扫描索引参与列 | Broker 确认发送成功时间，不代表已消费 |
| last_error | VARCHAR(100) | 可空 | — | 最近失败异常类型，不存凭据与正文 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 事件创建时间 |

- 唯一键：`uk_comment_rating_version(product_id, revision)`；扫描索引：`idx_comment_outbox_pending(published_at, next_attempt_at)`。

### window_reviews（窗口/店铺评价，单库实现）

| 字段 | 类型 | 约束 | 键 | 说明 |
|---|---|---|---|---|
| id | BIGINT | NOT NULL，自增 | 主键 | 内部主键 |
| review_id | CHAR(36) | NOT NULL | uk_window_review_id | 公开评价标识 |
| user_id | BIGINT | NOT NULL | uk_window_review_request；查询索引参与 | 评价用户内部编号 |
| author_name | VARCHAR(64) | NOT NULL | — | 评价时的用户昵称 |
| order_id | BIGINT | NOT NULL | uk_window_review_order | 已完成订单编号，一单一店铺一条 |
| window_id | BIGINT | NOT NULL | 窗口查询索引参与 | 所属窗口编号 |
| score | TINYINT | NOT NULL | 评分查询索引参与 | 店铺综合评分 1–5 |
| content | VARCHAR(1000) | NOT NULL | — | 评价正文（MySQL 存储） |
| image_file_keys | JSON | NOT NULL | — | 评价图片文件键数组 |
| tags | JSON | NOT NULL | — | 评价标签 |
| request_key | CHAR(36) | NOT NULL | uk_window_review_request | 提交幂等标识 |
| request_hash | CHAR(64) | NOT NULL | — | 提交内容摘要 |
| has_images | TINYINT | STORED 生成列 `JSON_LENGTH(image_file_keys)>0` | 带图筛选索引参与列 | 是否带图，自动生成不可写 |
| created_at | DATETIME | NOT NULL，默认当前时间 | — | 创建时间 |

- 唯一键：`uk_window_review_id`、`uk_window_review_order(order_id)`、`uk_window_review_request(user_id, request_key)`；窗口/用户两组「基础、按评分、按带图」索引同上。

MongoDB 集合 `comments`：`_id` 与 MySQL `comment_id` 一致；正文、图片键、标签、创建时间与 `committed` 可见标记——只有 MySQL 元数据提交后才对外可见。

**评分规则**：平均分 = 分数总和 / 评价数，HALF_UP 保留 2 位；无评价时均分为空；筛选只支持 1 星与 5 星。

## 核心业务流程

### 提交菜品评价

1. 幂等键校验（UUID）与内容 SHA-256 摘要；同键重放时按摘要比对，不一致返回 409。
2. 实时校验订单：已完成、本人订单、包含该菜品且数量大于 0，取窗口 ID。
3. 唯一键拦截一单菜重复评价。
4. 图片键逐一经文件服务校验归属（COMMENT_IMAGE 范围）后绑定。
5. Mongo 先插正文（committed=false）→ MySQL 事务（15 秒超时）提交元数据 + 评分累加 + Outbox；唯一键冲突回查幂等重放。
6. 标记 Mongo 提交（失败仅告警，由对账任务核实）；跨库结果不确定时不删 Mongo 文档。

### 窗口评价

与菜品评价同构（幂等摘要 + 订单已完成校验 + 图片校验），单库事务插入；一单一店铺一条。

### 评分事件发布

- 每秒扫描 Outbox 取 50 条：发送成功标记发布；失败按 `POW(2, min(attempts,8))` 秒指数退避。
- 每 5 分钟重投每个菜品的最新评分快照：重复事件不累加，幂等由商品服务投影保证；发送失败不阻塞评价提交。

## 幂等与并发控制

| 竞争点 | 处理方法 | 失败行为 |
|---|---|---|
| 评价重复提交 | 双唯一键：`(order_id, product_id)` 一单一菜一条 + `(user_id, request_key)` 幂等键；提交前先幂等回查，插入唯一键冲突后回查幂等重放 | 幂等命中返回首次结果；同键不同内容（SHA-256 摘要比对）返回 409 |
| 评分聚合并发累加 | `comment_rating_stats` 行 `FOR UPDATE`：同一菜品的评分累加串行化，revision 在锁内递增 | 行锁排队；CHECK 约束兜底数据自洽 |
| 事件重复发布 | Outbox 唯一键 `(product_id, revision)`：一版评分一条事件；发布器定时扫描 + 指数退避（`2^min(attempts,8)` 秒） | 发布失败不阻塞评价提交；每 5 分钟重投最新快照，消费侧 revision 单调幂等吸收 |
| MySQL/Mongo 跨库不一致 | 两阶段：Mongo 先写未提交态 → MySQL 提交后标记 `committed`；对账任务清理 24 小时仍未提交的孤儿文档（MySQL 可查则补标记，查不到才删除） | MySQL 成功而标记失败时由对账补齐，不丢正文；MySQL 失败则 Mongo 文档留待清理 |
| 结果不确定时的幂等重放 | 跨库提交结果不确定（超时）不删除 Mongo 文档，下次同幂等键重放按唯一键回查收敛 | 不产生重复评价，不产生孤儿误删 |
| 评分均分缓存击穿 | Redis 短缓存 TTL 随机 30–60 秒防雪崩；缓存未命中直接回源数据库 | 缓存故障回源数据库，不影响正确性 |
| 头像批量回源 | 批量 ≤50 一次回源，失败降级默认头像 | 不阻塞评价正文返回 |

## 接口清单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/comments` | CUSTOMER | 创建菜品评价（幂等键必需） |
| GET | `/comments/me` | CUSTOMER | 本人评价游标分页（按评分/是否含图筛选） |
| GET | `/comments/products/{productId}` | 公开 | 商品评价分页 |
| GET | `/comments/products/{productId}/rating` | 公开 | 商品均分（Redis 短缓存） |
| GET | `/comments/products/{productId}/summary` | 公开 | 总数/均分/好评/差评/图片数 |
| GET | `/comments/{commentId}` | 公开 | 评价详情 |
| GET | `/comments/orders/{orderId}` | CUSTOMER | 本人订单评价聚合（菜品 + 店铺） |
| GET | `/comments/ratings/batch?kind=&ids=` | 公开 | 批量评分（≤100） |
| POST | `/comments/window-reviews` | CUSTOMER | 创建窗口评价（幂等键必需） |
| GET | `/comments/window-reviews/me`、`/windows/{windowId}`、`.../rating`、`.../summary` | 见路径 | 窗口评价查询与均分 |

## 消息、异步与缓存设计

| 项 | 内容 |
|---|---|
| 生产 | `product-rating-changed-v1`（Outbox + 定时扫描 + 指数退避 + 快照重投） |
| 消费方 | 商品服务（更新商品评分投影） |
| 缓存 | 评分均分 Redis 短缓存 30–60 秒；故障时回源数据库 |
| 对账 | 孤儿 Mongo 文档清理：MySQL 可查则标记提交，查不到才删除 |

## 安全设计

JWT 验签 + jti 黑名单（黑名单不可确认拒绝）；用户 ID 取自 JWT；头像回源与图片校验用共享内部令牌。

## 配置项清单

| 配置 | 默认 | 说明 |
|---|---|---|
| `comment.publish-delay-ms` | 1000 | Outbox 扫描间隔 |
| `comment.rating-reconcile-ms` | 300000 | 评分快照重投间隔 |
| `comment.orphan-scan-ms` | — | 孤儿文档清理间隔 |
| `comment.messaging.enabled` | — | 消息发布开关 |
| `file.service-base-url` / `identity.user-service-base-url` / `internal.scope.token` | — | 直连地址与共享令牌 |

## 异常与失败行为

- 订单校验失败按 401/403/404/503 映射；订单不可用不能评价（fail-closed）。
- Mongo 正文缺失返回「评价内容暂不可用」；头像失败降级为默认头像不阻塞正文。
- `has_images` 回填记录缺失时抛 503（不静默假设无图）。
- 并发由两个唯一键吸收；评分 revision 由 FOR UPDATE 行锁串行化。

## 关键代码入口

`CommentServiceImpl`（跨库两阶段与幂等）→ `RatingEventPublisher`（Outbox 发布与重试）→ `CommentOrphanCleaner`（跨库对账）→ `comment_db` 迁移脚本。

## 内容限制与配套约定

- 图片限制：每份评价（菜品/店铺各自）最多 3 张且互不占用配额；单张 ≤5MB、单请求 ≤6MB；标签最多 5 个、每个 ≤20 字符。
- 正文按纯文本渲染，前端不得按 HTML 解释。
- 消息链路约定：商品侧消费组 `product-rating-v1`，死信队列 `%DLQ%product-rating-v1`，Broker 重试 16 次；主题 4 读 4 写队列；商品服务需激活 `comment-mq` profile。
- 防护边界：完整布隆过滤器、热点互斥重建与 Sentinel 流控首期未实现，仅评分均分短缓存。
- 孤儿对账节奏：每小时扫描一次、每轮最多 100 条，清理对象为超过 24 小时仍未提交的 Mongo 文档。
- 游标隔离：cursor 绑定用户/目标/评分/带图筛选组合，跨筛选范围不可复用；`has_images` 回填未完成时汇总的图片数为 null，带图筛选明确提示暂不可用。

## 已知限制与未验证项

- 无评价删除、无商家回复、无追评。
- 评分均分只保留两位小数，无评分分布直方图。
- 跨库提交的最终一致依赖定时对账，极端情况下正文可见性有分钟级延迟。

## 关联文档

- `docs/database/migrations/comment_db/`：建表脚本。
- `product-service/README.md`：评分事件消费与商品评分投影。
- 根 README「跨模块业务链路」：评价 → 评分 → 商品详情链路。
