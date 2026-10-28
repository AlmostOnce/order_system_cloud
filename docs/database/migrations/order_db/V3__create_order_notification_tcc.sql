USE order_db;

-- TCC 栅栏独立于 AT 订单行；保留取消墓碑以防空回滚后 Try 悬挂。
CREATE TABLE IF NOT EXISTS order_notification_tcc (
    xid VARCHAR(128) NOT NULL COMMENT 'Seata 全局事务标识',
    order_id BIGINT NOT NULL COMMENT '订单内部编号',
    order_version INT NOT NULL COMMENT '订单状态版本',
    recipient_id BIGINT NOT NULL COMMENT '接收用户内部编号',
    status VARCHAR(16) NOT NULL COMMENT 'PREPARED、CONFIRMED 或 CANCELLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (xid),
    KEY idx_notification_tcc_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='取消订单通知 TCC 状态表';
