-- 文件服务：创建 file_db 数据库、文件对象表与数据库账号。
-- 执行方式（root 权限，幂等可重复执行）：
--   wsl -- bash -c "docker exec -i order-system-cloud-mysql-1 mysql -uroot -porder_root_dev_2026 < /mnt/e/order_system_cloud/docs/database/migrations/file_db/V1__create_file_objects_table.sql"
-- 执行后需确认 file_service 账号已授权（本文件末尾包含账号授权语句）。

CREATE DATABASE IF NOT EXISTS file_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'file_service'@'%' IDENTIFIED BY 'file_password_dev_2026';
GRANT ALL PRIVILEGES ON file_db.* TO 'file_service'@'%';
FLUSH PRIVILEGES;

USE file_db;

CREATE TABLE IF NOT EXISTS file_objects (
    file_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件记录主键',
    file_key      CHAR(32)        NOT NULL COMMENT '对外文件键（UUID 去横线），接口只暴露此值，不暴露自增 ID',
    biz_type      VARCHAR(32)     NOT NULL COMMENT '业务类型：AVATAR/WINDOW_IMAGE/PRODUCT_IMAGE/RESTAURANT_IMAGE/COMMENT_IMAGE/OTHER',
    biz_ref       BIGINT UNSIGNED     NULL COMMENT '关联业务主键，上传时未知可为空',
    storage_type  VARCHAR(16)     NOT NULL DEFAULT 'LOCAL' COMMENT '存储介质：LOCAL/MINIO/S3',
    object_key    VARCHAR(512)    NOT NULL COMMENT '介质内相对路径，由服务端按内容摘要生成，绝不拼接用户输入',
    original_name VARCHAR(255)    NOT NULL COMMENT '原始文件名，仅作展示，不参与落盘路径',
    file_ext      VARCHAR(16)     NOT NULL COMMENT '扩展名（小写、白名单内）',
    content_type  VARCHAR(128)    NOT NULL COMMENT 'MIME 类型，由魔数校验后确定',
    file_size     BIGINT UNSIGNED NOT NULL COMMENT '字节数',
    sha256        CHAR(64)        NOT NULL COMMENT '内容摘要，用于秒传去重与完整性校验',
    uploader_id   BIGINT UNSIGNED NOT NULL COMMENT '上传者 user_id（取自 JWT sub）',
    is_deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id),
    UNIQUE KEY uk_file_key (file_key),
    KEY idx_sha256 (sha256),
    KEY idx_uploader (uploader_id, biz_type),
    KEY idx_biz (biz_type, biz_ref)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件对象元数据';
