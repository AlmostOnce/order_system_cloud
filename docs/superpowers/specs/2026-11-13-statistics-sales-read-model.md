# 统计服务销量读模型约定

## 目标

让 `statistics-service` 统一承担学生端销量榜和当前自然月销量展示，并为后续日榜、月榜及管理端统计留出清晰的数据归属。订单服务仍是订单与支付事实的唯一所有者；统计服务只消费已支付事件并维护可重建的查询模型。

## 已确认的业务口径

- 菜品销量按订单原始购买份数累计，只有已成功支付的订单计入；待支付和已取消订单不计入。
- 退款不回减已支付销量。全额退款的订单仍保留原销量贡献；财务退款额属于后续独立统计口径。
- 支付归属时间为 `payment_db.payments.success_time`。订单首次从待支付进入已支付时写入 `orders.paid_at`，后续履约、完成和退款均不得改写。
- 统计服务按新鲜事件异步更新 MySQL 事实投影和 Redis ZSet 排名；MySQL 是重建来源，Redis 丢失后可从读模型恢复。
- 旧的累计销量榜迁至统计服务；订单服务的 outbox 仍在订单状态事务内写入，RocketMQ 继续异步传递。
- 开发时区使用 `Asia/Shanghai`，当前月以 `paid_at` 的本地自然月计算。
- 用户端只向 CUSTOMER 提供读取接口；本期不将店主/经理统计页面放入 uni-app。

## 接口约定

- `GET /api/statistics/rankings/sales?limit=50`：累计销量榜，响应沿用 `{updatedAt, items:[{rank,productId,windowId,productName,sales}]}`。
- `GET /api/statistics/windows/monthly-sales`：当前月窗口汇总 `{items:[{windowId,sales}]}`，供首页一次批量读取。
- `GET /api/statistics/windows/{windowId}/monthly-sales`：当前窗口与菜品当前月销量 `{windowId,sales,products:[{productId,sales}]}`，供点餐页一次读取。

## 一致性和兼容

- 支付服务从支付表读取数据库写定的 `success_time`，通过内部接口传给订单服务；空值不能用订单更新时间或当前时间猜测。
- 订单状态、`paid_at` 和销量 outbox 在订单库同一本地事务提交。重复支付通知只允许首次状态迁移产生事件。
- 消费者以稳定 `order-paid-sales:{orderId}` 作为事件幂等键；事件事实、明细和 MySQL 汇总本地原子写入。Redis Lua 按订单去重，失败时允许 RocketMQ 重投；周期重建以 MySQL 汇总和事件集合为准。
- 新消息携带支付时间、窗口和下单时菜品快照。旧消息若只有订单 ID，消费者可调用订单服务受内部令牌保护的只读快照接口；历史记录由迁移脚本按 `payments.success_time` 回填。

## 数据保留

统计事实仅保存订单 ID、窗口 ID、支付时间和订单菜品快照，不复制用户身份、支付凭证或退款敏感信息。该模型在首期提供 ALL 与当前 MONTH 汇总；不提前实现未确认的排行榜切换 UI、财务报表和管理界面。
