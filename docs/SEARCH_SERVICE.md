# 菜品搜索服务

## 首期范围

`search-service`（端口 9106）以 Elasticsearch 8.x 保存可重建的当前可售菜品索引。商品服务仍是数据事实源：目录管理事务写入 `product_search_outbox`，发布器通过 RocketMQ `product-search-refresh-v1` 可靠重试；搜索服务按消息中的菜品 ID 读取单菜品最新快照并更新或删除文档。窗口、分类及食堂关联信息变化时刷新受影响窗口；旧版只有窗口 ID 的消息仍按窗口刷新。

索引每条文档对应一个菜品，含菜名、描述、价格、图片、分类、窗口、餐厅和位置。菜名、描述及层级名称使用 Elasticsearch 内置 `cjk` analyzer；菜名采用 `search_as_you_type` 支持词前缀/联想匹配，不需要额外安装中文分词插件。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/search/products?q=...&page=1&size=20` | 顾客搜索；支持 `restaurantId`、`windowId`、`categoryId` 过滤；每页最多 100 条 |
| POST | `/internal/search/rebuild` | 从商品主库全量重建；内部直连并提供 `X-Internal-Token`，不经网关 |
| GET | `/internal/products/search/windows` | 商品服务内部接口：可检索窗口 ID |
| GET | `/internal/products/search/windows/{windowId}/documents` | 商品服务内部接口：窗口当前可售菜品快照 |
| GET | `/internal/products/search/windows/{windowId}/products/{productId}` | 商品服务内部接口：单个可售菜品快照；不可售时返回空列表 |

搜索接口要求 `CUSTOMER` JWT。关键词匹配菜名、菜品描述、分类、窗口名和餐厅名。

## 初始化和运行配置

1. **已完成（2026-10-28）**：在 `product_db` 执行 V9 创建 outbox，并执行 V10 增加可空 `product_id`。V10 执行前的备份为 `C:\Users\DELL\.codex\backups\order_system_cloud\product_db_before_search_incremental_20261028_153245.sql`，SHA-256 为 `e393c468dbfa83a0310276972f39bbf584cd1d65c0a753635b5550b8355ed935`；迁移前后 outbox 均为 0 条。
2. 使用 Docker Compose `search` profile 启动 Elasticsearch；默认地址为 `http://127.0.0.1:9200`。
3. RocketMQ `mq` profile 的初始化器会创建 `product-search-refresh-v1` Topic。
4. product-service 与 search-service 默认包含 `search-mq` profile；两端 `ROCKETMQ_NAMESERVER`、`INTERNAL_SCOPE_TOKEN` 必须一致。升级时先停止所有旧版搜索消费者，再部署能识别新旧消息的搜索消费者，最后部署商品生产者；旧消费者仍写旧物理索引，不能与新版本混跑。运行中的 MQ 主题是否已创建仍需核实。
5. 搜索服务为已有 `menu-products-v1` 建立 `menu-products-current` 别名；空索引首次初始化时后台重建，失败后重试。后续启动已有数据时不执行全量同步。增量事件和窗口刷新使用共享读锁及窗口锁；全量重建使用写锁，在独立物理索引写入并核对文档数量后原子切换别名，旧索引保留。消息处理失败由 MQ 重试；默认每天 03:30 对比商品快照和索引，修复缺失、过期或多余的文档。
6. 将 `docs/nacos/gateway-service.yaml` 中 search-service 路由发布到 Nacos 后，经网关访问 `/api/search/**`。

2026-10-28 已从商品服务快照向旧物理索引写入 75 道可售菜品，并以相同查询条件确认“鸡肉”命中 8 道菜。增量同步、别名切换及对账是本次新源码，尚未构建或运行验收；当前运行的旧服务尚未使用这些改动。同步是异步最终一致；直接改 MySQL 不产生事件，由定期对账补偿。同步使用窗口锁内读取最新快照处理重复和乱序消息，当前尚未实现独立的持久化版本号或栅栏令牌，不能声称 Redis 锁丢失后的旧写入有版本隔离。旧索引与失败影子索引不会自动清理，需人工确认后管理。
