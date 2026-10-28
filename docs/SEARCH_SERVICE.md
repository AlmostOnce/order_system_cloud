# 菜品搜索服务

## 首期范围

`search-service`（端口 9106）以 Elasticsearch 8.x 保存可重建的当前可售菜品索引。商品服务仍是数据事实源：目录管理事务写入 `product_search_outbox`，发布器可靠重试 RocketMQ `product-search-refresh-v1`；搜索服务消费窗口刷新事件后，通过受 `X-Internal-Token` 保护的商品内部快照接口读取最新菜单，再替换该窗口的索引文档。事件不复制商品数据，重复消费按窗口重建，结果幂等。

索引每条文档对应一个菜品，含菜名、描述、价格、图片、分类、窗口、餐厅和位置。菜名、描述及层级名称使用 Elasticsearch 内置 `cjk` analyzer；菜名采用 `search_as_you_type` 支持词前缀/联想匹配，不需要额外安装中文分词插件。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/search/products?q=...&page=1&size=20` | 顾客搜索；支持 `restaurantId`、`windowId`、`categoryId` 过滤；每页最多 100 条 |
| POST | `/internal/search/rebuild` | 从商品主库全量重建；内部直连并提供 `X-Internal-Token`，不经网关 |
| GET | `/internal/products/search/windows` | 商品服务内部接口：可检索窗口 ID |
| GET | `/internal/products/search/windows/{windowId}/documents` | 商品服务内部接口：窗口当前可售菜品快照 |

搜索接口要求 `CUSTOMER` JWT。关键词匹配菜名、菜品描述、分类、窗口名和餐厅名。

## 初始化和运行配置

1. **已完成（2026-10-28）**：在 `product_db` 执行 V9，创建 `product_search_outbox`；执行前备份数据库，表结构核对通过，初始记录数为 0。
2. 使用 Docker Compose `search` profile 启动 Elasticsearch；默认地址为 `http://127.0.0.1:9200`。
3. RocketMQ `mq` profile 的初始化器会创建 `product-search-refresh-v1` Topic。
4. product-service 与 search-service 均使用 `search-mq` profile；两端 `ROCKETMQ_NAMESERVER`、`INTERNAL_SCOPE_TOKEN` 必须一致。
5. search-service 首次启动时创建索引；通过带内部令牌的重建接口索引现有菜单。之后目录管理变更由 outbox + RocketMQ 自动刷新。
6. 将 `docs/nacos/gateway-service.yaml` 中 search-service 路由发布到 Nacos 后，经网关访问 `/api/search/**`。

数据库 outbox 迁移已执行；本次没有启动 Elasticsearch / RocketMQ / 服务或调用搜索接口，也没有运行 Maven 构建或测试，因此消息及搜索功能尚未进行运行态验收。索引刷新为异步最终一致；全量重建按窗口逐个替换，期间搜索结果可能暂时不完整。备份位置见 `docs/PROJECT_PROGRESS.md`。
