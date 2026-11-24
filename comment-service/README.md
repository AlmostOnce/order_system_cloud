# comment-service 评论服务

> 提供菜品评价、窗口评价、订单评价查询、评分汇总与评分事件发布。默认端口 `9105`，关系库为 `comment_db`，评价正文等内容存 MongoDB。

## 职责与边界

- 菜品评价：正文、图片、标签、评分、点赞数、商家回复。
- 窗口评价：订单所属窗口的整体体验评价。
- 订单评价汇总：按订单查询菜品评价与窗口评价。
- 评分汇总：菜品、窗口平均分与评价数量。
- 购买校验：创建评价前通过订单服务核对订单归属、状态与购买明细。
- 评分事件：通过本地消息表发布 `product-rating-changed-v1`，商品服务更新评分投影。

**边界：** 评论服务不直接读写订单库或商品库；订单事实通过 `order-api` 获取，用户公开信息与头像通过 `user-api` 获取。

## 混合存储

- MySQL 保存关系元数据：评价归属、订单、用户、菜品、窗口、评分、状态、时间、评分汇总与事件 outbox。
- MongoDB 保存内容文档：正文、图片数组、标签数组、商家回复、点赞数。
- 写入顺序固定：先写 MongoDB 文档，再写 MySQL 元数据。
- 若 MySQL 写入失败，可能产生暂时不可见的孤儿文档，由 `CommentOrphanCleaner` 延迟检查并清理已确认无对应元数据的文档。
- 读取以 MySQL 可见性为依据；MySQL 已提交但内容缺失时应明确失败，不能伪造成正常评价。

## 评价规则

- 菜品评价要求订单属于本人、状态满足评价条件且订单中包含该菜品。
- 同一订单同一菜品有唯一约束；请求携带稳定的 `Idempotency-Key` 处理重试。
- 窗口评价要求订单属于本人且已满足评价状态，按订单与窗口唯一。
- 图片先上传文件服务，再提交 `fileKey`；服务端核验文件归属与类型，不信任任意外部 URL。
- 评分变化事件带版本号，商品侧只接受更新版本，旧事件不能覆盖新评分。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `POST /comments` | 创建菜品评价 |
| `GET /comments/me` | 本人菜品评价 |
| `GET /comments/products/{productId}` | 菜品评价分页 |
| `GET /comments/{commentId}` | 评价详情 |
| `GET /comments/products/{productId}/rating` | 菜品评分汇总 |
| `GET /comments/products/{productId}/summary` | 菜品评价摘要 |
| `GET /comments/ratings/batch` | 批量评分 |
| `POST /comments/window-reviews` | 创建窗口评价 |
| `GET /comments/window-reviews/me` | 本人窗口评价 |
| `GET /comments/window-reviews/windows/{windowId}` | 窗口评价分页 |
| `GET /comments/window-reviews/windows/{windowId}/rating` | 窗口评分 |
| `GET /comments/window-reviews/windows/{windowId}/summary` | 窗口评价摘要 |
| `GET /comments/orders/{orderId}` | 订单评价汇总 |

## 数据与存储

MySQL：

| 表 | 作用 |
|---|---|
| `comments` | 菜品评价元数据 |
| `window_reviews` | 窗口评价元数据 |
| 评分汇总/事件 outbox 相关表 | 评分统计与可靠事件 |

MongoDB：

| 集合 | 作用 |
|---|---|
| 评价内容集合 | 正文、图片、标签、商家回复、点赞数 |

迁移脚本位于 [`docs/database/migrations/comment_db`](../docs/database/migrations/comment_db)，包含评论、窗口评价、评分筛选索引和图片筛选相关变更。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`CommentController.java`](src/main/java/com/hbue/ordering/comment/controller/CommentController.java) | 菜品评价接口 |
| [`WindowReviewController.java`](src/main/java/com/hbue/ordering/comment/controller/WindowReviewController.java) | 窗口评价接口 |
| [`OrderReviewController.java`](src/main/java/com/hbue/ordering/comment/controller/OrderReviewController.java) | 订单评价汇总 |
| [`CommentServiceImpl.java`](src/main/java/com/hbue/ordering/comment/service/impl/CommentServiceImpl.java) | 菜品评价写入与查询 |
| [`CommentMetadataWriterImpl.java`](src/main/java/com/hbue/ordering/comment/service/impl/CommentMetadataWriterImpl.java) | MySQL 元数据与事件写入 |
| [`WindowReviewServiceImpl.java`](src/main/java/com/hbue/ordering/comment/service/impl/WindowReviewServiceImpl.java) | 窗口评价 |
| [`PurchaseVerifierImpl.java`](src/main/java/com/hbue/ordering/comment/service/impl/PurchaseVerifierImpl.java) | 购买事实校验 |
| [`RatingEventPublisher.java`](src/main/java/com/hbue/ordering/comment/messaging/RatingEventPublisher.java) | 评分事件发布 |
| [`CommentOrphanCleaner.java`](src/main/java/com/hbue/ordering/comment/task/CommentOrphanCleaner.java) | 孤儿文档清理 |
| [`CommentMongoConfig.java`](src/main/java/com/hbue/ordering/comment/config/CommentMongoConfig.java) | MongoDB 配置 |

## 排查与运行

- 评价提交失败：先看订单归属/状态/购买校验，再看幂等键与唯一约束。
- 评价列表有记录但内容缺失：检查 MongoDB 文档与孤儿清理记录，不能当作正常评价展示。
- 商品评分未更新：检查评分 outbox、`product-rating-changed-v1` 消费与事件版本。
- 详细设计见 [`docs/COMMENT_SERVICE.md`](../docs/COMMENT_SERVICE.md)、[`docs/database/COMMENT_MAINTENANCE.md`](../docs/database/COMMENT_MAINTENANCE.md)。
