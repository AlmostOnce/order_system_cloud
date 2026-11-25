# product-api 商品契约模块

> product-service 的内部调用契约：核价、档口/餐厅查询、搜索源、推荐源。

## Feign 客户端

| 客户端 | 用途 | 降级约定 |
|---|---|---|
| `ProductServiceClient` | 下单核价（以商品库在售状态与现价为准，规格无效整体拒绝） | 失败统一服务不可用，不得用客户端金额兜底 |
| `WindowInternalClient` | 档口/餐厅内部查询与批量窗口名（数据范围校验用） | 失败不伪造资源信息 |
| `ProductSearchSourceClient` | 搜索服务读商品主数据快照 | — |
| `ProductRecommendationSourceClient` | 推荐服务目录回填与候选复核 | — |

## 模型

`ProductQuoteRequest/Response/ItemRequest`、`ProductQuoteVO`、`WindowInfoVO`、`RestaurantInfoVO`、`ProductSearchDocument`、`ProductRecommendationDocument`。

## 使用约定

被购物车、推荐、订单、优惠券、用户服务依赖；核价结果（含规格键与规格快照）是订单价格唯一事实源。
