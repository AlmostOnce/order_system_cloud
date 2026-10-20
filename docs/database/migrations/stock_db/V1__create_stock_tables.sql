-- 库存服务数据库：库存主表、库存流水与 Seata AT 回滚日志。
-- 本脚本幂等，可重复执行。

CREATE DATABASE IF NOT EXISTS stock_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 库存服务专用数据库账号，仅授予 stock_db 权限。
-- 与 .env 中的 STOCK_DB_PASSWORD 保持一致。
CREATE USER IF NOT EXISTS 'stock_service'@'%' IDENTIFIED BY 'stock_password_dev_2026';
GRANT ALL PRIVILEGES ON stock_db.* TO 'stock_service'@'%';
FLUSH PRIVILEGES;

USE stock_db;

-- 库存主表：DB 为唯一事实来源，Redis 仅作加速快照
CREATE TABLE IF NOT EXISTS stock (
    stock_id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存记录主键',
    window_id     BIGINT UNSIGNED NOT NULL COMMENT '所属档口 ID',
    product_id    BIGINT UNSIGNED NOT NULL COMMENT '菜品 ID',
    total_qty     INT             NOT NULL DEFAULT 0 COMMENT '总可售上限',
    available_qty INT             NOT NULL DEFAULT 0 COMMENT '当前可售数量，DB 为准',
    version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    is_deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (stock_id),
    UNIQUE KEY uk_window_product (window_id, product_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存主表';

-- 库存流水：幂等记账 + 对账依据
CREATE TABLE IF NOT EXISTS stock_flow (
    flow_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '流水主键',
    biz_type    VARCHAR(32)     NOT NULL COMMENT '业务类型：ORDER_DEDUCT/ORDER_RESTORE/ADMIN_ADJUST',
    biz_ref     VARCHAR(64)     NOT NULL COMMENT '业务单据号（订单号），用于幂等',
    stock_id    BIGINT UNSIGNED NOT NULL COMMENT '库存记录 ID',
    window_id   BIGINT UNSIGNED NOT NULL COMMENT '档口 ID（冗余，便于按档口对账）',
    product_id  BIGINT UNSIGNED NOT NULL COMMENT '菜品 ID（冗余）',
    change_qty  INT             NOT NULL COMMENT '变动数量：扣减为负、回补为正',
    before_qty  INT             NOT NULL COMMENT '变动前可售数量',
    after_qty   INT             NOT NULL COMMENT '变动后可售数量',
    operator_id BIGINT UNSIGNED     NULL COMMENT '操作者 user_id，系统触发可为空',
    remark      VARCHAR(255)        NULL COMMENT '备注',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (flow_id),
    UNIQUE KEY uk_biz_flow (biz_type, biz_ref, stock_id),
    KEY idx_stock (stock_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存流水';

-- Seata AT 回滚日志（官方标准结构）
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL COMMENT '分支事务 ID',
    xid           VARCHAR(128) NOT NULL COMMENT '全局事务 ID',
    context       VARCHAR(128) NOT NULL COMMENT 'undo_log 序列化方式',
    rollback_info LONGBLOB     NOT NULL COMMENT '回滚信息',
    log_status    INT          NOT NULL COMMENT '0 正常 1 已防御',
    log_created   DATETIME(6)  NOT NULL COMMENT '创建时间',
    log_modified  DATETIME(6)  NOT NULL COMMENT '修改时间',
    PRIMARY KEY (branch_id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AT 模式回滚日志';
