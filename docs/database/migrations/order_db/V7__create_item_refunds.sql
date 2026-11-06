-- 单次执行；先备份orders和order_items，新增退款表及计数不改变历史订单金额/状态。
USE order_db;
ALTER TABLE orders ADD COLUMN completed_at DATETIME NULL COMMENT '首次取餐完成时间，售后72小时期限依据';
-- 历史完成订单在本次改造前没有独立完成时间，使用既有最后更新时间回填，今后不再随售后变化。
UPDATE orders SET completed_at=updated_at WHERE status='已完成' AND completed_at IS NULL;
ALTER TABLE order_items
 ADD COLUMN refund_reserved_quantity INT NOT NULL DEFAULT 0 COMMENT '已申请且未关闭的数量，含已退款，防止重复申请',
 ADD COLUMN refund_approved_quantity INT NOT NULL DEFAULT 0 COMMENT '已批准含处理中/已退款数量，不再参与履约',
 ADD COLUMN refunded_quantity INT NOT NULL DEFAULT 0 COMMENT '已确认退款和库存步骤完成的数量',
 ADD CONSTRAINT chk_item_refund_quantities CHECK (refunded_quantity>=0 AND refunded_quantity<=refund_approved_quantity
  AND refund_approved_quantity<=refund_reserved_quantity AND refund_reserved_quantity<=quantity);
CREATE TABLE order_refunds (
 refund_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '退款主键',
 refund_no CHAR(32) NOT NULL COMMENT '稳定退款号，跨服务幂等依据',
 request_key VARCHAR(64) NOT NULL COMMENT '学生申请幂等键',
 order_id BIGINT NOT NULL COMMENT '订单主键', user_id BIGINT NOT NULL COMMENT '订单所属学生',
 item_id BIGINT NOT NULL COMMENT '订单菜品项主键', product_id BIGINT NOT NULL COMMENT '菜品主键',
 product_name VARCHAR(128) NOT NULL COMMENT '订单菜品名称快照', quantity INT NOT NULL COMMENT '本次申请数量',
 amount DECIMAL(10,2) NOT NULL COMMENT '服务端按实付明细分摊计算的金额',
 reason VARCHAR(500) NOT NULL COMMENT '学生申请原因',
 status VARCHAR(24) NOT NULL COMMENT 'PENDING待商家/REJECTED商家拒绝/APPEALED介入/APPROVED已批准/PROCESSING执行中/SUCCESS完成/CLOSED关闭',
 original_status VARCHAR(32) NOT NULL COMMENT '申请时订单状态',
 restock TINYINT NOT NULL DEFAULT 0 COMMENT '本次批准是否回库存，完成订单恒为0',
 decision_reason VARCHAR(500) NULL COMMENT '最近处理说明',
 decided_by BIGINT NULL COMMENT '最近审批人，系统自动审批为空',
 due_at DATETIME NOT NULL COMMENT '商家处理截止时间，申请后72小时',
 next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次执行或租约到期时间',
 attempts INT NOT NULL DEFAULT 0 COMMENT '累计执行次数',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近更新时间',
 PRIMARY KEY(refund_id), UNIQUE KEY uk_refund_no(refund_no), UNIQUE KEY uk_refund_request(user_id,request_key),
 KEY idx_refund_order(order_id,refund_id), KEY idx_refund_due(status,due_at),
 KEY idx_refund_retry(status,next_attempt_at,refund_id), KEY idx_refund_window_queue(status,order_id,refund_id),
 CONSTRAINT chk_refund_quantity CHECK(quantity>0), CONSTRAINT chk_refund_amount CHECK(amount>0),
 CONSTRAINT chk_refund_status CHECK(status IN ('PENDING','REJECTED','APPEALED','APPROVED','PROCESSING','SUCCESS','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按菜品数量退款单及持久化执行任务';
CREATE TABLE order_refund_actions (
 action_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '处理轨迹主键', refund_id BIGINT NOT NULL COMMENT '退款主键',
 actor_id BIGINT NULL COMMENT '操作者，系统为空', action VARCHAR(32) NOT NULL COMMENT '申请/批准/拒绝/介入/关闭/到账',
 detail VARCHAR(500) NOT NULL COMMENT '原因或处理说明', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
 PRIMARY KEY(action_id), KEY idx_refund_actions(refund_id,action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款不可覆盖的审批轨迹';
