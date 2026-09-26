# 订单创建幂等

`POST /orders` 必须带 `Idempotency-Key` 请求头，长度为 1 至 128 个非空白字符。

- 相同用户、相同键、相同请求内容：返回已经创建的订单。
- 相同用户、相同键、不同请求内容：返回 HTTP 409（`COMMON_409`）。
- 不同用户可以使用相同键。
- 请求内容摘要覆盖窗口 ID、金额和备注；金额会忽略无意义的小数位差异。
- 数据库唯一索引 `(user_id, idempotency_key)` 负责处理并发重试。Redis/Redisson 锁不是幂等保障。

## 应用数据库迁移

当前项目没有自动执行数据库迁移的依赖。现有 `order_db` 已有 `orders` 表时，只需对该数据库执行一次 `V2__add_order_idempotency.sql`。全新数据库则按顺序执行 `V1__create_orders_table.sql` 和 `V2__add_order_idempotency.sql`。

迁移完成后再启动新版 `order-service`。`idempotency_key` 和 `request_hash` 对历史订单保留为 `NULL`；新订单由服务写入这两个字段。
