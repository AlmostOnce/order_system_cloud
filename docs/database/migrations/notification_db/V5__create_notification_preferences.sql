-- 目标 notification_db；新增用户通知偏好表，允许按订单、反馈、聊天分类关闭站内提醒。
-- 缺省没有记录即视为开启，只有用户显式关闭才写入 enabled=0，避免注册时批量写默认值。
-- 关闭只影响实时提示和未读角标，消息中心已落库的通知记录与聊天消息仍完整保留。
-- 执行时必须带 --default-character-set=utf8mb4，否则中文注释会被客户端按 latin1 二次编码成乱码。
SET NAMES utf8mb4;

USE notification_db;

CREATE TABLE IF NOT EXISTS notification_preferences
(
    user_id    BIGINT      NOT NULL COMMENT '用户内部编号，与通知接收人一致',
    category   VARCHAR(32) NOT NULL COMMENT '通知分类：ORDER 订单、SUPPORT 反馈工单、CHAT 商家聊天',
    enabled    TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '1 开启提醒，0 关闭提醒',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (user_id, category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户通知偏好';
