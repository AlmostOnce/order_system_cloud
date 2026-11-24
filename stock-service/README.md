# stock-service 库存服务

> 维护窗口菜品库存与库存变动流水，提供内部扣减、回补、退款回补和批量查询。默认端口 `9104`，数据库 `stock_db`，数据库数量是库存事实来源。

## 职责与边界

- 保存每个窗口菜品的可用库存、总量基线与乐观锁版本。
- 提供内部批量查询、窗口库存查询、单菜品库存查询。
- 下单扣减：按订单业务标识幂等扣减并写流水。
- 取消回补：核对原扣减流水后回补，防重复恢复。
- 退款回补：部分退款成功后按退款数量回补，并记录退款来源。
- 库存变更与事件 outbox 同事务落库，异步通知缓存失效。

**边界：** 库存服务没有公开网关路由，只通过 `stock-api` 供订单、商品、推荐等服务内部调用。Redis 只用于库存快照与原子辅助，不能替代数据库事实。

## 扣减流程

1. 对目标库存行加锁并读取记录。
2. 检查同一业务类型、业务单据和库存记录是否已有流水；已存在则按幂等返回。
3. 执行 Redis 快照预扣；快照不可用时按代码降级策略处理。
4. 以数据库版本与可用数量条件更新作为最终判据。
5. 成功后写入 `stock_flow` 和 `stock_event_outbox`。
6. Redis 与数据库不一致时以数据库结果为准，定时对账发现漂移。

当前实现先获取数据库行锁，再做 Redis 预扣，因此不能描述成“库存不足请求都在访问数据库前被过滤”。

## 回补与退款

- 取消回补业务类型为 `ORDER_RESTORE`，与下单扣减 `ORDER_DEDUCT` 使用不同类型，使同一订单可以“先扣后补”各记一次，同时各自幂等。
- 回补前核对原扣减流水；没有原扣减不会凭空增加库存。
- 退款回补记录退款来源（迁移 `V3__partial_refund_origin.sql`），支持部分退款多次回补。
- 管理调整使用 `ADMIN_ADJUST`。

## 异步事件

- `stock_event_outbox` 与库存变更同事务写入，后台发布到 `stock-changed-v1`。
- 消费者组 `stock-cache-invalidator-v1` 按事件幂等处理并使相关缓存失效。
- 带 Seata XID 的记录需要等待全局事务完成后再发布；`StockEventPublisher` 以 `undo_log` 状态作为门控，该方案依赖真实 Seata 时序，需联调验收。
- `StockReconcileTask` 定时对账，修复 Redis 快照与数据库漂移。

## 主要接口

| 方法与路径 | 说明 |
|---|---|
| `POST /internal/stocks/recommendations/batch` | 推荐场景批量查询可售库存 |
| `GET /internal/stocks/windows/{windowId}` | 窗口库存 |
| `GET /internal/stocks/{windowId}/{productId}` | 单菜品库存 |
| `POST /internal/stocks/deduct` | 下单扣减 |
| `POST /internal/stocks/restore` | 取消回补 |
| `POST /internal/stocks/refund` | 退款回补 |

## 数据表

| 表 | 作用 |
|---|---|
| `stock` | 库存主表：窗口、菜品、总量、可用数量、版本、逻辑删除 |
| `stock_flow` | 库存流水：业务类型、业务引用、变动数量、变动前后数量、操作人、备注 |
| `stock_event_outbox` | 库存变更事件本地消息表 |
| `stock_event_consume`（消费幂等表） | 缓存失效消费者去重记录 |
| `undo_log` | Seata AT 回滚日志 |

`stock` 关键字段：`stock_id`、`window_id`、`product_id`、`total_qty`、`available_qty`、`version`、`is_deleted`。

`stock_flow` 关键字段：`biz_type`（`ORDER_DEDUCT` / `ORDER_RESTORE` / `ADMIN_ADJUST`）、`biz_ref`、`stock_id`、`window_id`、`product_id`、`change_qty`（扣减为负、回补为正）、`before_qty`、`after_qty`、`operator_id`、`remark`。

## 关键代码入口

| 文件 | 作用 |
|---|---|
| [`StockInternalController.java`](src/main/java/com/hbue/ordering/stock/controller/StockInternalController.java) | 内部扣减、回补、查询 |
| [`StockRefundInternalController.java`](src/main/java/com/hbue/ordering/stock/controller/StockRefundInternalController.java) | 退款回补 |
| [`StockServiceImpl.java`](src/main/java/com/hbue/ordering/stock/service/impl/StockServiceImpl.java) | 扣减与回补业务 |
| [`StockRefundServiceImpl.java`](src/main/java/com/hbue/ordering/stock/service/impl/StockRefundServiceImpl.java) | 退款回补业务 |
| [`StockFlowTypes.java`](src/main/java/com/hbue/ordering/stock/support/StockFlowTypes.java) | 流水业务类型常量 |
| [`StockEventOutboxWriter.java`](src/main/java/com/hbue/ordering/stock/messaging/StockEventOutboxWriter.java) | 事件 outbox 写入 |
| [`StockEventPublisher.java`](src/main/java/com/hbue/ordering/stock/messaging/StockEventPublisher.java) | 事件发布 |
| [`StockEventConsumer.java`](src/main/java/com/hbue/ordering/stock/messaging/StockEventConsumer.java) | 缓存失效消费 |
| [`StockReconcileTask.java`](src/main/java/com/hbue/ordering/stock/task/StockReconcileTask.java) | 定时对账 |

## 迁移与运行

- 迁移脚本位于 [`docs/database/migrations/stock_db`](../docs/database/migrations/stock_db)。
- 下单与取消依赖 Seata 全局事务；Seata 未运行时扣减会失败，不能把失败当成库存不足。
- 排查库存异常时核对库存行、扣减/回补流水、outbox 与 Seata 日志，不要手工改数掩盖问题。
- 详细接入说明见 [`docs/STOCK_INTEGRATION.md`](../docs/STOCK_INTEGRATION.md)。
