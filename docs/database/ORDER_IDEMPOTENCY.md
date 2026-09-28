# 订单创建幂等

`POST /orders` 必须带 `Idempotency-Key` 请求头，长度为 1 至 128 个非空白字符。

- 相同用户、相同键、相同请求内容：返回已经创建的订单。
- 相同用户、相同键、不同请求内容：返回 HTTP 409（`COMMON_409`）。
- 不同用户可以使用相同键。
- 创建请求由窗口 ID、菜品 ID、数量和备注组成；客户端不提交价格或订单金额。
- 请求摘要覆盖窗口 ID、排序后的菜品 ID/数量和备注。菜品顺序不影响摘要；服务端核价结果不纳入摘要，因此同一请求重试时即使当前售价变化，也会返回首次创建的订单及其价格快照。
- 数据库唯一索引 `(user_id, idempotency_key)` 负责处理并发重试。Redis/Redisson 锁不是幂等保障。
- 订单头和 `order_items` 菜品快照在同一数据库事务内保存；明细写入失败时订单头也会回滚。

请求示例：

```json
{
  "windowId": 3,
  "items": [
    { "productId": 101, "quantity": 2 },
    { "productId": 202, "quantity": 1 }
  ],
  "remark": "少辣"
}
```

订单服务调用商品服务的 `POST /internal/products/quote` 批量核价。商品服务只返回启用窗口中属于该窗口且在售的菜品；缺少任一菜品时，订单创建返回 HTTP 400。订单金额由服务端按核价乘数量计算，创建响应和订单详情包含下单时的菜品名称、编码、单价、数量及行金额快照。

重复菜品 ID 必须由调用方先合并成一行；每单最多 100 行。核价接口要求有效的 CUSTOMER JWT，并校验 Access Token 黑名单。

## 应用数据库迁移

当前项目没有自动执行数据库迁移的依赖。现有 `order_db` 已有 `orders` 表时，按需执行 `V2__add_order_idempotency.sql` 和 `V3__create_order_items_table.sql`。全新数据库则按顺序执行 `V1__create_orders_table.sql`、`V2__add_order_idempotency.sql` 和 `V3__create_order_items_table.sql`。

迁移完成后再启动新版 `order-service`。`idempotency_key` 和 `request_hash` 对历史订单保留为 `NULL`；新订单由服务写入这两个字段。
