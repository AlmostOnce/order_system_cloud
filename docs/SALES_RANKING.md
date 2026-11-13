# 学生端销量榜与月售读模型

## 当前实现

销量查询归 `statistics-service`（9111），不再由订单服务直接汇总订单或维护活动排行榜。订单服务仍是订单及首次支付事实的所有者，并通过本地事务 outbox 发布销量事件。

```text
支付成功时间 success_time
        ↓
order-service：支付状态 CAS + orders.paid_at + 销量 outbox（同一事务）
        ↓ RocketMQ：order-paid-sales-v2（新事件）/ order-paid-sales-v1（兼容消费）
statistics-service：事件幂等落库 + 订单明细快照 + ALL/MONTH 汇总
        ↓
MySQL statistics_db（事实与可重建汇总） → Redis ZSet（累计榜缓存）
        ↓
Gateway → CUSTOMER 学生端
```

统计服务读取 CUSTOMER JWT，并再次拒绝非顾客角色。旧订单排行榜地址只保留为带原始 Authorization 透传的兼容代理；新学生端直接调用统计服务路由。

## 统计口径

- 只累计成功支付订单的菜品原始购买份数；待支付和已取消订单不计入。
- 退款不冲减历史销量。退款金额和净营收属于后续财务统计，不混入销量口径。
- 月份取支付服务持久化的 `payments.success_time`，首次支付时写入 `orders.paid_at`；订单后续状态变化不得改写。
- 月售使用 `Asia/Shanghai` 自然月。首次支付的日期决定归属月份，不使用下单时间或更新时间推测。
- 商品按下单时菜品 ID、名称、窗口和份数快照统计；同一菜品在不同窗口分别计数。

## 接口

| 学生端用途 | 网关地址 | 返回内容 |
|---|---|---|
| 累计菜品榜 | `GET /api/statistics/rankings/sales?limit=50` | `{updatedAt, items:[{rank, productId, windowId, productName, sales}]}` |
| 首页窗口本月销量 | `GET /api/statistics/windows/monthly-sales` | `{items:[{windowId, sales}]}` |
| 点餐页窗口及菜品本月销量 | `GET /api/statistics/windows/{windowId}/monthly-sales` | `{windowId, sales, products:[{productId, sales}]}` |

端点仅提供聚合数据，不返回订单号、用户标识、支付凭证或支付明细。首页批量获取所有窗口，点餐页按窗口一次获取菜品数据，避免逐窗口/逐菜品请求。

## 写入、幂等与恢复

1. 支付服务把数据库中的 `success_time` 传给订单内部接口。订单服务仅允许待支付状态 CAS 的胜者首次写入 `paid_at` 和销量 outbox； outbox 与状态在同一个 `order_db` 事务内提交。
2. 订单 outbox 发布器同步等待 RocketMQ 发送确认，确认后标记已发布；失败保留待发送记录并退避重试。网络发送不放在支付状态数据库事务中。
3. 新 V2 消息包含支付时间、窗口和订单菜品快照。统计服务仍消费 V1 旧消息；旧消息只有订单号时，通过带内部令牌的订单只读接口取得已支付快照。
4. 统计服务在 `statistics_db` 本地事务中写入订单事实、菜品明细及窗口/菜品 ALL 与 MONTH 汇总。事件键和订单号唯一约束使 MQ 至少一次投递可重复消费而不重复累计。
5. MySQL 事务提交后才更新 Redis。Redis ZSet 是累计榜缓存，不是事实源；更新失败可安全重试，缓存缺失或版本落后时使用 MySQL 汇总重建。Lua 将缓存内订单去重、ZSet 更新和版本推进原子化，分布式锁保护重建过程。
6. 统计接口失败不阻塞首页或菜单核心数据；学生端月售显示 `--`。支付刚成功到 MQ 投影完成之间允许短暂延迟。

## 开发库迁移及本次核对

- `docs/database/migrations/order_db/V8__order_paid_at.sql` 为订单添加 nullable `paid_at`，只用真实成功支付记录回填空值；脚本固定 `utf8mb4`，避免命令行字符集导致中文状态筛选漏记。
- `docs/database/migrations/statistics_db/V1__create_sales_read_model.sql` 创建事实表、明细表、窗口/菜品汇总、投影版本及最小权限服务账号，并从已有订单与支付事实初始化读模型。
- 2026-11-13 已备份 `order_db`、`payment_db` 后执行两份脚本。最终核验：18 笔订单中 12 笔成功支付可关联，`paid_at` 与成功支付时间不一致为 0，未支付订单写入 `paid_at` 为 0；事实表 12 个订单事件、14 条菜品明细、共 28 份。
- 窗口及菜品汇总均为累计 28 份；按支付月份为 2026-10 共 25 份、2026-11 共 3 份；投影版本 12。应用账号只获 `statistics_db` 的读写权限，凭配置登录读取事实表成功。
- 首次运行时发现 CLI 字符集未固定，DDL 成功但中文状态回填未命中；检查确认没有写入错误销量。脚本补上 `SET NAMES utf8mb4` 后重跑，并完成以上核验。
- 备份文件、SHA-256、服务端口和当前未验证项见 [统计读模型实施计划](superpowers/plans/2026-11-13-statistics-sales-read-model.md)。

## 验收边界

当前源码、配置与开发库迁移已完成；本次未运行 Maven、构建、测试或重启服务。统计服务、RocketMQ 消费、Redis 缓存重建、Gateway/Nacos 路由及 Android 真机显示仍需在用户启动最新服务后联调。旧版订单服务榜单消费者及重建任务已停用，历史类保留不代表它们仍参与运行。
