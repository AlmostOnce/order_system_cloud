# product-service 食堂与商品服务

> 管理食堂、窗口、菜品分类、菜品、规格与图片，对外提供菜单、报价、搜索源和推荐源。默认端口 `9093`，数据库 `product_db`。

## 职责与边界

- 食堂资料：名称、位置、公告、图片、排序、启停状态。
- 窗口资料：所属食堂、楼层位置、简介、图片组、营业状态、排序。
- 菜品目录：分类、菜品、价格、上下架、排序、图片、规格。
- 顾客菜单：按窗口读取分类与在售菜品；分页、缓存与稳定性排序。
- 商品报价：为购物车核价和下单提供权威价格、可售状态与规格校验。
- 内部数据源：为搜索服务、推荐服务提供增量快照和校验接口。
- 评分投影：消费评论评分事件，维护菜品评分读模型。
- 管理能力：按角色与数据范围限制食堂、窗口、分类、菜品的增删改。

**边界：** 库存事实在 `stock-service`，商品服务不把库存快照当最终依据；图片二进制在 `file-service`，业务表只保存 `fileKey`。

## 菜单与报价

- `GET /products/restaurants`：食堂列表。
- `GET /products/windows`：窗口列表，可按食堂筛选。
- `GET /products/windows/{windowId}/menu`：窗口菜单，按分类组织在售菜品，未分类菜品单独归集。
- `GET /products/windows/{windowId}/stocks`：转发库存服务查询窗口库存。
- `POST /internal/products/quote`：内部批量报价，返回当前价格、可售状态与规格选项；下单与购物车核价使用。

食堂排序决定食堂之间的顺序，窗口排序决定同一食堂内的顺序，菜品排序决定窗口内顺序；排序只影响展示，不是唯一标识。

## 规格

- 一个菜品可配置多组规格（如份量、辣度），每组包含多个选项及加价。
- 管理端在事务内完整替换启用配置，提交后清理菜单缓存。
- 菜单与报价只读取启用配置；下单时订单会保存所选规格快照。
- 规格设计见 [`docs/superpowers/specs/2026-11-17-product-specifications-design.md`](../docs/superpowers/specs/2026-11-17-product-specifications-design.md)。

## 缓存与评分

- 菜单缓存使用 Redis，键由 `ProductCacheKeys` 统一管理；变更后按窗口失效。
- 布隆过滤器用于降低不存在菜品的无效查询，初始化与重建由 `ProductBloomFilterInitializer` / `ProductBloomFilterManager` 负责。
- 评分投影表保存菜品平均分与评价数量；消费 `product-rating-changed-v1`，按事件版本更新，旧事件不能覆盖新版本。

## 搜索与推荐数据源

- 菜品、分类、窗口变更与 `product_search_outbox` 同事务落库，后台发布 `product-search-refresh-v1`。
- 搜索服务收到事件后调用 `/internal/products/search/...` 读取最新快照，决定写入或删除索引文档。
- 推荐服务通过 `/internal/products/recommendations/...` 按游标拉取全量商品特征，并通过 `/validate` 校验候选是否仍可售。

## 管理权限

- 食堂排序仅允许 `GENERAL_MANAGER`（且具备全平台范围）修改。
- `RESTAURANT_MANAGER` 只能维护自身食堂范围内的窗口、分类与菜品。
- `STALL_MANAGER` 只能维护授权窗口内的菜品与分类；窗口和食堂资料由上级角色维护。
- 所有写操作在服务端核对数据范围，不能只依赖前端菜单隐藏。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `GET /products/restaurants` | 顾客端食堂列表 |
| `GET /products/windows` | 顾客端窗口列表 |
| `GET /products/windows/{windowId}/menu` | 窗口菜单 |
| `GET /products/windows/{windowId}/stocks` | 窗口库存 |
| `GET /products/{productId}/rating` | 菜品评分投影 |
| `GET /products/images` | 批量解析图片地址 |
| `GET /products/admin/windows`、`POST/PUT/DELETE` | 窗口管理 |
| `GET /products/admin/restaurants`、`POST/PUT/DELETE` | 食堂管理 |
| `GET/POST/PUT/DELETE /products/admin/windows/{windowId}/categories` | 分类管理 |
| `GET/POST/PUT/DELETE /products/admin/windows/{windowId}/products` | 菜品管理 |
| `GET/PUT /products/admin/products/{productId}/specifications` | 规格管理 |
| `PUT /products/admin/windows/{windowId}/images`、`/image` | 窗口图片 |
| `PUT /products/admin/products/{productId}/image` | 菜品图片 |
| `POST /internal/products/quote` | 内部报价 |
| `GET /internal/products/search/**` | 搜索快照 |
| `GET /internal/products/recommendations/**` | 推荐快照与校验 |

## 数据表

| 表 | 作用 |
|---|---|
| `restaurants` | 食堂资料：编码、名称、位置、电话、图片、公告、排序、状态 |
| `dining_windows` | 窗口：所属食堂、编码、名称、简介、位置、图片、启停、营业标记、排序 |
| `product_categories` | 窗口内分类：名称、排序、状态 |
| `products` | 菜品：窗口、分类、编码、名称、描述、图片、价格、售卖状态、排序、评分投影 |
| `product_specification_groups` | 规格组：菜品、名称、必选标记、排序 |
| `product_specification_options` | 规格选项：所属组、名称、加价、排序 |
| `product_search_outbox` | 搜索索引刷新事件本地消息表 |

关键字段：

- `restaurants`：`restaurant_code` 唯一业务编码、`sort_order` 越小越靠前、`status` 1 启用 0 停用。
- `dining_windows`：`restaurant_id` 所属食堂、`is_open` 营业标记、`image_file_key` 主图、图片组由图片表/字段扩展维护。
- `products`：`window_id`、`category_id`（空表示未分类）、`product_code` 窗口内唯一、`price` 单位元、`sale_status` 1 上架 0 下架。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`ProductMenuServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductMenuServiceImpl.java) | 食堂/窗口/菜单查询 |
| [`ProductQuoteServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductQuoteServiceImpl.java) | 内部报价 |
| [`ProductAdministrationServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductAdministrationServiceImpl.java) | 窗口、分类、菜品管理 |
| [`RestaurantAdministrationServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/RestaurantAdministrationServiceImpl.java) | 食堂管理 |
| [`ProductSpecificationServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductSpecificationServiceImpl.java) | 规格读写 |
| [`ProductSearchSourceServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductSearchSourceServiceImpl.java) | 搜索数据源 |
| [`ProductRecommendationSourceServiceImpl.java`](src/main/java/com/hbue/ordering/product/service/impl/ProductRecommendationSourceServiceImpl.java) | 推荐数据源 |
| [`ProductRatingMessaging.java`](src/main/java/com/hbue/ordering/product/rating/ProductRatingMessaging.java) | 评分事件消费 |
| [`ProductSearchOutboxPublisher.java`](src/main/java/com/hbue/ordering/product/messaging/ProductSearchOutboxPublisher.java) | 搜索 outbox 发布 |
| [`ProductMenuCacheService.java`](src/main/java/com/hbue/ordering/product/cache/ProductMenuCacheService.java) | 菜单缓存 |
| [`DefaultDataScopeService.java`](src/main/java/com/hbue/ordering/product/scope/DefaultDataScopeService.java) | 商品侧数据范围判定 |

## 迁移与运行

- 迁移脚本位于 [`docs/database/migrations/product_db`](../docs/database/migrations/product_db)。
- `CREATE TABLE IF NOT EXISTS` 不会给旧表补列，升级前先核对现有结构与迁移前置条件。
- 修改商品后若搜索未更新，先检查 outbox、`product-search-refresh-v1` 消费和索引状态；直接改库不会自动产生事件。
- 菜单缓存最长需等待过期或主动失效，调试时不要误判为代码未生效。
- 详细目录与数据说明见 [`docs/database/PRODUCT_CATALOG.md`](../docs/database/PRODUCT_CATALOG.md)。
