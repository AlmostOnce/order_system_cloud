-- 优惠券到期提醒上线后同步字段注释，避免注释仍只提 SUPPORT 造成误解。
-- 只重写注释，不改变列类型、可空性、默认值和数据，可重复执行。
SET NAMES utf8mb4;

USE notification_db;

ALTER TABLE notifications
    MODIFY COLUMN biz_type VARCHAR(16) NULL COMMENT '业务类型，SUPPORT 表示反馈工单，COUPON 表示优惠券到期提醒',
    MODIFY COLUMN biz_id VARCHAR(64) NULL COMMENT '业务公开标识，配合 biz_type 跳转详情';

ALTER TABLE notification_preferences
    MODIFY COLUMN category VARCHAR(32) NOT NULL COMMENT '通知分类：ORDER 订单、SUPPORT 反馈工单、COUPON 优惠券到期、CHAT 商家聊天';
