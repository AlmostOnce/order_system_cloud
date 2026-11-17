-- 订单推荐事实迁移；先执行既有V9规格快照迁移，再加载包含本次Outbox写入的订单代码。
-- 本脚本只新增结构化规格历史及事件表，不改写既有支付、退款和累计销量事实。
-- JSON默认表达式需要本项目使用的MySQL 8.0.13+；旧行表示未知结构化选择 []，不查询今天的规格补造历史。
SET NAMES utf8mb4;
USE order_db;

SET @recommendation_selection_column_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='order_db' AND TABLE_NAME='order_items'
      AND COLUMN_NAME='specification_selection_snapshot'
);
SET @recommendation_selection_ddl = IF(
    @recommendation_selection_column_exists=0,
    'ALTER TABLE order_db.order_items ADD COLUMN specification_selection_snapshot JSON NOT NULL DEFAULT (JSON_ARRAY()) COMMENT ''下单时结构化规格组与选项快照，旧订单未知为[]'' AFTER specification_text_snapshot',
    'SELECT 1'
);
PREPARE recommendation_selection_statement FROM @recommendation_selection_ddl;
EXECUTE recommendation_selection_statement;
DEALLOCATE PREPARE recommendation_selection_statement;

-- 一版订单最多一份不可变快照；支付确认和成功退款事务均先更新源版本再插入此表。
-- 此迁移不会为历史订单自动产生事件；存量回填须通过受保护的有界源快照契约完成。
CREATE TABLE IF NOT EXISTS recommendation_purchase_outbox (
    event_id VARCHAR(96) NOT NULL COMMENT 'recommendation-purchase:订单ID:源版本',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '订单服务内部主键',
    order_version INT NOT NULL COMMENT '快照生成时的源订单版本',
    payload JSON NOT NULL COMMENT '含全部规格明细、购买与成功退款份数的推荐快照',
    attempts BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '派发失败次数，不影响已确认的源业务事实',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次允许重试时间',
    published_at DATETIME NULL COMMENT 'Broker同步确认后写入',
    last_error VARCHAR(200) NULL COMMENT '异常类型，不保存购买明细或凭据',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本地源事务保存时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_recommendation_purchase_version (order_id, order_version),
    KEY idx_recommendation_purchase_pending (published_at, next_attempt_at),
    CONSTRAINT fk_recommendation_purchase_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='订单支付及成功退款的推荐全量快照Outbox';
