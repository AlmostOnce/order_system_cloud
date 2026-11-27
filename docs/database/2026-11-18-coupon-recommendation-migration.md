# 优惠券与推荐服务开发库迁移记录

执行日期：2026-11-18。目标为本机 `127.0.0.1:9095`，WSL `Ubuntu-24.04` 中的 `order-system-cloud-mysql-1`，MySQL 8.0.46，实例主机名
`08592035d3f6`。

## 执行前检查

- 初次执行前，`coupon_db`、`recommendation_db` 及对应服务账号均不存在，因此无需备份这两个新库的业务数据。
- 后续执行订单库配套迁移前，确认目标是同一 MySQL 8.0.46 开发实例；`order_db` 原有 9 张表、18 笔订单和 20 条明细。`orders.order_id` 为主键，V9 的 `specification_text_snapshot` 列已存在；优惠券快照列、结构化规格快照列及两个 Outbox 表均不存在。
- 订单库迁移前使用 `mysqldump --single-transaction` 备份至 `C:\Users\DELL\AppData\Local\Temp\order_db-before-coupon-recommendation-20261118.sql`（31,587 字节，SHA-256：`826AA29B96192F03534870DF6E34253097E18B308A7AFA1FBF8F3E22E9FD32B8`）。
- 收藏 Outbox 迁移前确认 `user_db.user_favorites` 已存在且为 0 行，`user_favorite_event_outbox` 不存在；`user_service` 对 `user_db` 有建表权限。V12 只执行 `CREATE TABLE IF NOT EXISTS`，不改动已有收藏，因此无需额外备份。
- 已检查订单库脚本：推荐迁移只补结构化规格快照列并建 Outbox，不生成历史事件；优惠券迁移为既有订单及明细回填应付金额，不删除订单或重置业务数据。

## 实际执行

依次成功执行：

1. `coupon_db/V1__create_coupon_tables.sql`
2. `recommendation_db/V1__create_recommendation_tables.sql`
3. `recommendation_db/V2__create_recommendation_source_cursors.sql`
4. `order_db/V10__recommendation_purchase_snapshots.sql`
5. `order_db/V10__add_coupon_amount_snapshots.sql`
6. `user_db/V12__create_favorite_event_outbox.sql`

以上路径均相对于 `docs/database/migrations/`。

## 核验结果与进度

- `coupon_db` 已创建 6 张表，包含 `undo_log`；各表均为 0 行。
- `recommendation_db` 已创建 7 张表，回填游标表包含 `purchase-orders`、`favorite-relations` 两条初始化记录，其余表均为 0 行。
- `order_db` 新增 `recommendation_purchase_outbox`、`order_coupon_consume_outbox` 两张表，均为空；推荐 Outbox 外键指向 `orders(order_id)`。
- `user_db` 新增 `user_favorite_event_outbox`，表及索引已核验；当前 0 行，既有 `user_favorites` 仍为 0 行。迁移不为历史收藏生成事件。
- `order_db` 新增订单优惠券快照列、明细优惠金额列及 `specification_selection_snapshot` JSON 列。既有 18 笔订单的正金额应付金额已回填为原订单金额，20 条明细的正金额实付金额已回填为原明细金额；核验无不一致记录，结构化规格快照均为 `[]`。
- 实际表集合与迁移脚本的建表集合一致。
- `coupon_service`、`recommendation_service` 均使用各自服务 `application.yml` 的默认开发凭据登录成功，并查询了本库每张表。
- 优惠券账号仅在 `coupon_db.*` 上拥有库级全部权限；推荐账号仅在 `recommendation_db.*` 上拥有
  `SELECT/INSERT/UPDATE/DELETE` 权限，均无全局管理或其他业务库授权。
- 两个新服务自身的建库、建表、建号及订单库、用户库配套结构迁移均已完成。未运行 Maven、测试、服务启动或重启；未验证消息消费、Seata 事务、推荐历史回填和接口链路。

开发密码仅适用于当前开发环境；正式部署需要独立配置凭据。
