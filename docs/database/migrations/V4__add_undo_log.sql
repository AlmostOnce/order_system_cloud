-- order_db 追加 Seata AT 回滚日志表。
-- 下单链路的全局事务（order × stock）在 order_db 上同样需要 undo_log 支撑本地分支回滚。
-- 本脚本幂等，可重复执行。

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
