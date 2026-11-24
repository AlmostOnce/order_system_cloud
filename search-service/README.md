# search-service 搜索服务

> 提供菜品全文搜索与索引维护。默认端口 `9106`，使用 Elasticsearch 保存可重建的搜索读模型，商品库是唯一事实来源。

## 职责与边界

- 按关键词、食堂、窗口、分类等条件搜索可售菜品。
- 消费 `product-search-refresh-v1`，读取商品服务最新快照后增量更新或删除索引文档。
- 初始化与重建索引：写入新物理索引，校验后切换别名。
- 定时对账，发现索引与商品事实不一致时补齐。
- 记录搜索意图并异步发送给推荐服务，用于推荐画像。

**边界：** Elasticsearch 不是商品主库；直接修改 MySQL 不会自动产生索引事件。搜索不可用时不能伪造商品事实。

## 索引设计

- 查询固定使用别名 `menu-products-current`，避免直接面对构建中的物理索引。
- 文档包含菜名、描述、分类、窗口、食堂、价格、可售状态等检索字段。
- 菜名使用 `search_as_you_type` 与 `cjk` 分析器，其他文本字段使用 `cjk`；中文匹配效果仍取决于实际文本与查询方式。
- 旧索引在切换后保留以便回滚，需要人工清理。

## 增量同步

1. 商品变更与 `product_search_outbox` 在商品库同事务落库。
2. 商品服务后台发布 `product-search-refresh-v1`。
3. 搜索服务消费后调用商品服务 `/internal/products/search/...` 读取最新快照。
4. 可售商品写入/更新文档，不可售或删除商品移除文档。
5. 读取最新状态可降低重复与乱序影响，但仍需版本栅栏与对账兜底。

## 重建与对账

- `SearchIndexBootstrap` 在启动或触发时检查索引与别名，必要时构建新索引。
- `SearchIndexManager` 负责别名创建与原子切换。
- `SearchIndexLock` 使用分布式锁避免多实例同时重建；锁过期与旧写入竞争仍需运行验收。
- `SearchIndexReconcileTask` 定时比较索引与商品事实，补充遗漏。
- 内部重建入口 `POST /internal/search/rebuild` 需要内部凭据。

## 搜索意图

搜索成功后异步把关键词、筛选条件和用户身份发送给推荐服务内部接口 `/internal/recommendations/search-intents`。该调用失败不影响搜索结果返回，但会丢失画像信号。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `GET /search/products?q=...` | 菜品搜索，支持食堂、窗口、分类条件 |
| `POST /internal/search/rebuild` | 内部索引重建 |

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`ProductSearchController.java`](src/main/java/com/hbue/ordering/search/controller/ProductSearchController.java) | 搜索接口 |
| [`ProductSearchServiceImpl.java`](src/main/java/com/hbue/ordering/search/service/impl/ProductSearchServiceImpl.java) | 查询与筛选 |
| [`ProductSearchRefreshConsumer.java`](src/main/java/com/hbue/ordering/search/messaging/ProductSearchRefreshConsumer.java) | 增量刷新消费 |
| [`SearchIndexManager.java`](src/main/java/com/hbue/ordering/search/support/SearchIndexManager.java) | 索引与别名管理 |
| [`SearchIndexBootstrap.java`](src/main/java/com/hbue/ordering/search/task/SearchIndexBootstrap.java) | 启动初始化 |
| [`SearchIndexReconcileTask.java`](src/main/java/com/hbue/ordering/search/task/SearchIndexReconcileTask.java) | 定时对账 |
| [`SearchIndexLock.java`](src/main/java/com/hbue/ordering/search/support/SearchIndexLock.java) | 重建锁 |
| [`MenuProductDocument.java`](src/main/java/com/hbue/ordering/search/document/MenuProductDocument.java) | 索引文档结构 |
| [`SearchIntentRecorderImpl.java`](src/main/java/com/hbue/ordering/search/service/impl/SearchIntentRecorderImpl.java) | 搜索意图上报 |

## 排查与运行

- 搜索无结果：确认商品可售、Elasticsearch 与搜索服务可用、索引已初始化、别名指向正确索引。
- 搜索有延迟：检查商品 outbox、MQ 消费组 `product-search-index-v1` 与消费日志。
- 重建失败：检查磁盘、索引权限与重建锁；失败时不要直接删除正在使用的别名。
- 详细设计见 [`docs/SEARCH_SERVICE.md`](../docs/SEARCH_SERVICE.md)。
