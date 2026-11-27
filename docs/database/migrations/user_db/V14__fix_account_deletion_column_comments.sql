-- 修正学生账号注销三个字段的中文注释。
-- V13 在部分环境执行时，mysql 客户端未指定 utf8mb4，注释被按 latin1 二次编码成乱码；
-- 本脚本只重写列注释，不改变列类型、可空性、默认值和数据。
-- 执行时必须带 --default-character-set=utf8mb4；MODIFY COLUMN 可重复执行，结果一致。
SET NAMES utf8mb4;

USE user_db;

ALTER TABLE users
    MODIFY COLUMN deletion_requested_at DATETIME NULL COMMENT '注销申请提交时间';

ALTER TABLE users
    MODIFY COLUMN deletion_effective_at DATETIME NULL COMMENT '注销生效时间，申请后默认72小时';

ALTER TABLE users
    MODIFY COLUMN deletion_reminded_at DATETIME NULL COMMENT '注销生效前24小时提醒邮件发送时间';
