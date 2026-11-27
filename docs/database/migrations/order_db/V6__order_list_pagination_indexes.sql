-- 我的订单游标分页索引。执行前备份orders并核对SHOW INDEX FROM orders；执行后核验索引及EXPLAIN并记录结果。
-- 2026-11-06已检查开发库：仅有(user_id,created_at)、用户幂等键等索引，没有以下组合。
-- 全部状态：等值user_id + order_id范围/倒序；单状态：等值user_id/status + order_id范围/倒序。
-- active包含三个状态，MySQL可能仍需合并排序，最终返回由LIMIT限制；必须用实际数据EXPLAIN验收。
-- 只新增索引，不删除原created_at索引，不修改任何订单、金额、状态或库存。
ALTER TABLE orders
    ADD INDEX idx_orders_user_order (user_id, order_id) COMMENT '本人全部订单主键游标分页',
    ADD INDEX idx_orders_user_status_order (user_id, status, order_id) COMMENT '本人状态订单主键游标分页';
-- 本脚本只能执行一次；若已存在等价索引，应跳过对应ADD，禁止重复创建。
-- 回退需确认查询无依赖后人工DROP上述新增索引，不删除业务数据；不在此提供自动破坏性回退。
