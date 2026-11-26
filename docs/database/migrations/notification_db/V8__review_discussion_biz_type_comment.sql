USE notification_db;

-- 新增评价评论通知后，biz_type 增加 COMMENT 与 WINDOW_REVIEW 两个取值，
-- 偏好分类增加 REVIEW。只更新列注释，不改变类型与长度：
-- VARCHAR(16) 足以容纳 WINDOW_REVIEW，VARCHAR(32) 足以容纳 REVIEW。
-- MODIFY COLUMN 可重复执行，不会丢失数据。
ALTER TABLE notifications
    MODIFY COLUMN biz_type VARCHAR(16) NULL COMMENT '业务类型，SUPPORT 反馈工单，COUPON 优惠券到期，COMMENT 菜品评价评论，WINDOW_REVIEW 店铺评价评论',
    MODIFY COLUMN biz_id VARCHAR(64) NULL COMMENT '业务公开标识，配合 biz_type 跳转详情';

ALTER TABLE notification_preferences
    MODIFY COLUMN category VARCHAR(32) NOT NULL COMMENT '通知分类：ORDER 订单、SUPPORT 反馈工单、COUPON 优惠券到期、CHAT 商家聊天、REVIEW 评价互动';
