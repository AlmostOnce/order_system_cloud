-- 优惠券服务独占数据库。平台券的发行与每笔实际优惠成本在此留痕。
-- 建库/授权可重复执行；本文件内表均使用 IF NOT EXISTS，重复执行不会重置券库存或用户持券。
-- 首次部署默认密码与 coupon-service application.yml、.env.example 一致；部署环境应设置独立强密码并同步调整此处。

CREATE DATABASE IF NOT EXISTS coupon_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'coupon_service'@'%' IDENTIFIED BY 'coupon_password_dev_2026';
GRANT ALL PRIVILEGES ON coupon_db.* TO 'coupon_service'@'%';
FLUSH PRIVILEGES;

USE coupon_db;

CREATE TABLE IF NOT EXISTS coupon_template (
    coupon_template_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '优惠券模板主键',
    title              VARCHAR(120) NOT NULL COMMENT '学生端展示标题',
    min_spend          DECIMAL(12,2) NOT NULL COMMENT '满足使用资格的适用商品金额门槛',
    discount_amount    DECIMAL(12,2) NOT NULL COMMENT '固定优惠金额',
    total_quantity     BIGINT UNSIGNED NOT NULL COMMENT '本模板最多可领取数量',
    claimed_quantity   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已成功领取数量，原子条件更新维护',
    claim_start_at     DATETIME NOT NULL COMMENT '开始领取时间',
    claim_end_at       DATETIME NOT NULL COMMENT '停止领取时间',
    valid_from         DATETIME NOT NULL COMMENT '券可使用开始时间',
    valid_until        DATETIME NOT NULL COMMENT '券可使用截止时间',
    scope_type         VARCHAR(16) NOT NULL COMMENT '适用范围：CAMPUS/WINDOWS',
    issuer_type        VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '发行主体：首期仅 PLATFORM',
    funding_party      VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT '优惠承担方：首期仅 PLATFORM',
    status             VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CLAIM_STOPPED/STOPPED',
    created_by         BIGINT UNSIGNED NOT NULL COMMENT '创建人用户主键',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (coupon_template_id),
    KEY idx_coupon_claimable (status, claim_start_at, claim_end_at),
    CONSTRAINT chk_coupon_min_spend_positive CHECK (min_spend > 0),
    CONSTRAINT chk_coupon_discount_valid CHECK (discount_amount > 0 AND discount_amount < min_spend),
    CONSTRAINT chk_coupon_quantity_positive CHECK (total_quantity > 0 AND claimed_quantity <= total_quantity),
    CONSTRAINT chk_coupon_time_order CHECK (claim_start_at < claim_end_at AND valid_from < valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券发行模板';

CREATE TABLE IF NOT EXISTS coupon_template_window (
    coupon_template_id BIGINT UNSIGNED NOT NULL COMMENT '优惠券模板主键',
    window_id          BIGINT UNSIGNED NOT NULL COMMENT '限定适用窗口 ID；不建立跨服务外键',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '适用范围创建时间',
    PRIMARY KEY (coupon_template_id, window_id),
    KEY idx_coupon_window (window_id, coupon_template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板适用窗口';

CREATE TABLE IF NOT EXISTS user_coupon (
    user_coupon_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户券主键',
    coupon_template_id BIGINT UNSIGNED NOT NULL COMMENT '来源优惠券模板',
    user_id            BIGINT UNSIGNED NOT NULL COMMENT '领取用户主键',
    status             VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE/LOCKED/USED/EXPIRED/REVOKED',
    valid_from         DATETIME NOT NULL COMMENT '领取时固化的可用起始时间',
    valid_until        DATETIME NOT NULL COMMENT '领取时固化的可用截止时间',
    locked_order_no    VARCHAR(64) NULL COMMENT '当前绑定订单号；锁定期间唯一',
    claimed_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    used_at            DATETIME NULL COMMENT '确认支付后核销时间',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY uk_user_coupon_template (coupon_template_id, user_id),
    UNIQUE KEY uk_user_coupon_locked_order (locked_order_no),
    KEY idx_user_coupon_status (user_id, status, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生领取的优惠券';

CREATE TABLE IF NOT EXISTS coupon_use_record (
    coupon_use_record_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '券使用流水主键',
    user_coupon_id       BIGINT UNSIGNED NOT NULL COMMENT '用户券主键',
    coupon_template_id   BIGINT UNSIGNED NOT NULL COMMENT '优惠券模板主键',
    user_id              BIGINT UNSIGNED NOT NULL COMMENT '使用用户主键',
    order_no             VARCHAR(64) NOT NULL COMMENT '锁定/核销/释放关联订单号',
    status               VARCHAR(16) NOT NULL COMMENT '状态：LOCKED/USED/RELEASED',
    discount_amount      DECIMAL(12,2) NOT NULL COMMENT '订单锁定时的优惠金额快照',
    platform_funded_amount DECIMAL(12,2) NOT NULL COMMENT '平台承担的优惠成本快照',
    window_funded_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '窗口承担金额；首期固定为 0',
    locked_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次锁定时间',
    used_at              DATETIME NULL COMMENT '核销时间',
    released_at          DATETIME NULL COMMENT '释放时间',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (coupon_use_record_id),
    UNIQUE KEY uk_coupon_use_user_order (user_coupon_id, order_no),
    UNIQUE KEY uk_coupon_use_order (order_no),
    KEY idx_coupon_use_template (coupon_template_id, status, used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券锁定/核销/释放流水';

-- Seata AT 回滚日志：锁券/释放作为订单全局事务的本地分支。
CREATE TABLE IF NOT EXISTS undo_log (
    branch_id     BIGINT       NOT NULL COMMENT 'Seata 分支事务 ID',
    xid           VARCHAR(128) NOT NULL COMMENT 'Seata 全局事务 ID',
    context       VARCHAR(128) NOT NULL COMMENT 'undo_log 序列化方式',
    rollback_info LONGBLOB     NOT NULL COMMENT '回滚镜像数据',
    log_status    INT          NOT NULL COMMENT '0 正常 1 已防御',
    log_created   DATETIME(6)  NOT NULL COMMENT '创建时间',
    log_modified  DATETIME(6)  NOT NULL COMMENT '修改时间',
    PRIMARY KEY (branch_id),
    UNIQUE KEY ux_undo_log (xid, branch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Seata AT 回滚日志';

-- 管理操作审计：记录模板创建、发布、停止领取和停止使用，便于追溯平台预算券的生命周期变更。
CREATE TABLE IF NOT EXISTS coupon_admin_action_record (
    action_id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理操作流水主键',
    coupon_template_id BIGINT UNSIGNED NOT NULL COMMENT '被操作的优惠券模板 ID',
    operator_id        BIGINT UNSIGNED NOT NULL COMMENT '执行操作的总经理用户 ID',
    action_type        VARCHAR(24) NOT NULL COMMENT '操作类型：CREATE/PUBLISH/STOP_CLAIMS/STOP_USE',
    previous_status   VARCHAR(16) NULL COMMENT '操作前模板状态；创建时为空',
    new_status        VARCHAR(16) NOT NULL COMMENT '操作后模板状态',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (action_id),
    KEY idx_coupon_admin_action_template (coupon_template_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板管理审计流水';
