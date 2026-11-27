-- 为订单菜品明细保存下单时的规格选择键和规格文字快照。
-- 规格选择不改变库存粒度；这两列只服务历史订单展示、幂等和购物车清理核对。
-- 每列先检查 information_schema，允许脚本在连接中断后重复执行。
SET NAMES utf8mb4;

SET
@selection_key_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'order_db'
      AND TABLE_NAME = 'order_items'
      AND COLUMN_NAME = 'selection_key_snapshot'
);
SET
@selection_key_ddl = IF(
    @selection_key_column_exists = 0,
    'ALTER TABLE order_db.order_items ADD COLUMN selection_key_snapshot VARCHAR(256) NOT NULL DEFAULT ''base'' COMMENT ''下单时规格选项ID的规范化组合键，无规格为base'' AFTER unit_price_snapshot',
    'SELECT 1'
);
PREPARE selection_key_statement FROM @selection_key_ddl;
EXECUTE selection_key_statement;
DEALLOCATE PREPARE selection_key_statement;

SET
@specification_text_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'order_db'
      AND TABLE_NAME = 'order_items'
      AND COLUMN_NAME = 'specification_text_snapshot'
);
SET
@specification_text_ddl = IF(
    @specification_text_column_exists = 0,
    'ALTER TABLE order_db.order_items ADD COLUMN specification_text_snapshot VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''下单时规格组和选项文字快照，无规格为空'' AFTER selection_key_snapshot',
    'SELECT 1'
);
PREPARE specification_text_statement FROM @specification_text_ddl;
EXECUTE specification_text_statement;
DEALLOCATE PREPARE specification_text_statement;

-- 旧订单由列默认值表示没有规格，既不查询当前规格配置，也不推断历史选择。
