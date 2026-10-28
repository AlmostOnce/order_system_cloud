-- 库存事件 outbox 与消费者幂等记录；执行前备份 stock_db。
USE stock_db;

CREATE TABLE IF NOT EXISTS stock_event_outbox (
    event_id        VARCHAR(192) NOT NULL COMMENT '库存流水对应的稳定事件键',
    biz_type        VARCHAR(32)  NOT NULL COMMENT '库存变动类型',
    biz_ref         VARCHAR(64)  NOT NULL COMMENT '业务单据号',
    stock_id        BIGINT UNSIGNED NOT NULL COMMENT '库存记录 ID',
    window_id       BIGINT UNSIGNED NOT NULL COMMENT '档口 ID',
    product_id      BIGINT UNSIGNED NOT NULL COMMENT '菜品 ID',
    available_qty   INT NOT NULL COMMENT '该流水后的数据库可售数量，仅作通知快照',
    stock_version   INT NOT NULL COMMENT '该流水后的库存版本',
    xid             VARCHAR(128) NULL COMMENT 'Seata 全局事务 ID；空值表示普通本地事务',
    attempts        INT NOT NULL DEFAULT 0 COMMENT '发布失败次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次发布尝试时间',
    published_at    DATETIME NULL COMMENT 'Broker 确认接收时间',
    last_error      VARCHAR(255) NULL COMMENT '最近一次发布错误类型',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件创建时间',
    PRIMARY KEY (event_id),
    UNIQUE KEY uk_stock_event_flow (biz_type, biz_ref, stock_id),
    KEY idx_stock_event_pending (published_at, next_attempt_at, created_at),
    KEY idx_stock_event_xid (xid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '库存变更消息待发布表';

CREATE TABLE IF NOT EXISTS stock_event_consumed (
    event_id     VARCHAR(192) NOT NULL COMMENT '已处理的库存变更事件键',
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费处理时间',
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '库存变更消息消费幂等表';
