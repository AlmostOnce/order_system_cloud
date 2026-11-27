# comment-api 评价契约模块

> comment-service 的跨服务模型契约：纯 record 包，无第三方依赖、不含 Feign 客户端。

## 模型

| 类 | 职责 |
|---|---|
| `ProductRatingChangedEvent` | 评分变更事件（版本/事件 ID/商品/revision/评价数/分数总和），带 `valid()` 自校验 |
| `ProductRatingVO` | 菜品评分视图：HALF_UP 保留 2 位均分，无评价时均分为空 |

## 使用约定

被商品服务（MQ 消费评分事件做投影）与评价服务（自引用）依赖；事件幂等由商品侧 revision 单调比较保证。
