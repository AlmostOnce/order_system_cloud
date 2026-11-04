-- 执行前停止旧扣款实例，备份并检查账务；不修改余额和流水。
CREATE TABLE IF NOT EXISTS balance_settlement_guard (
 guard_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '结算屏障内部编号',
 user_id BIGINT NOT NULL COMMENT '所属用户内部编号',
 biz_type VARCHAR(32) NOT NULL COMMENT '业务类型',
 biz_ref VARCHAR(64) NOT NULL COMMENT '业务引用',
 amount DECIMAL(10,2) NOT NULL COMMENT '应扣金额',
 closed TINYINT NOT NULL DEFAULT 0 COMMENT '是否已关闭未扣款结算',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
 UNIQUE KEY uk_settlement_ref(user_id,biz_type,biz_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扣款与安全关闭共用的持久结算屏障';
