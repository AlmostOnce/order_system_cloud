-- 学生反馈工单库。建库、服务账号与两张表均可重复执行，不会覆盖已有工单数据。
-- 首次部署默认密码与 support-service application.yml、.env.example 保持一致；
-- 生产环境应通过环境变量设置独立强密码，并同步修改本脚本中的 GRANT 目标。

CREATE DATABASE IF NOT EXISTS support_db
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 开发环境最小服务账号：只授予 support_db 全库权限，不授予全局权限。
CREATE USER IF NOT EXISTS 'support_service'@'%' IDENTIFIED BY 'support_password_dev_2026';
GRANT ALL PRIVILEGES ON support_db.* TO 'support_service'@'%';
FLUSH PRIVILEGES;

USE support_db;

-- 工单主表：一行对应一名学生提交的一次反馈。
-- 内部自增主键只用于存储与游标分页；对外一律使用 ticket_id（UUID），避免暴露工单总量与提交顺序。
CREATE TABLE IF NOT EXISTS support_ticket
(
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    ticket_id       CHAR(36)     NOT NULL COMMENT '公开工单标识',
    customer_id     BIGINT       NOT NULL COMMENT '提交学生内部用户编号',
    type            VARCHAR(16)  NOT NULL COMMENT '工单类型：DINING/SOFTWARE',
    category        VARCHAR(32)  NOT NULL COMMENT '分类编码，服务端白名单校验',
    status          VARCHAR(16)  NOT NULL COMMENT '状态：PENDING/PROCESSING/CLOSED',
    order_id        BIGINT       NULL COMMENT '关联订单编号，仅用餐问题',
    order_no        VARCHAR(32)  NULL COMMENT '关联订单号快照，便于展示',
    window_id       BIGINT       NULL COMMENT '窗口编号，用餐问题必填',
    window_name     VARCHAR(64)  NULL COMMENT '窗口名称快照',
    restaurant_id   BIGINT       NULL COMMENT '所属餐厅编号，用于管理端数据范围',
    description     VARCHAR(1000) NOT NULL COMMENT '问题描述',
    image_file_keys JSON         NOT NULL COMMENT '图片文件键数组，最多 3 个',
    device_info     VARCHAR(255) NULL COMMENT '软件问题设备信息',
    customer_unread TINYINT      NOT NULL DEFAULT 0 COMMENT '是否有商家新回复：0 否，1 是',
    request_key     CHAR(36)     NOT NULL COMMENT '创建幂等键',
    request_hash    CHAR(64)     NOT NULL COMMENT '创建内容摘要，用于识别同键不同内容',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    last_reply_at   DATETIME(3)  NULL COMMENT '最近商家回复时间',
    closed_at       DATETIME(3)  NULL COMMENT '关闭时间',
    PRIMARY KEY (id),
    -- 公开标识全局唯一，详情接口只按此列查询。
    UNIQUE KEY uk_support_ticket_id (ticket_id),
    -- 同一学生同一幂等键只允许一条工单，数据库唯一约束承担并发去重。
    UNIQUE KEY uk_support_ticket_request (customer_id, request_key),
    -- 学生「我的反馈」按内部主键倒序游标分页。
    KEY idx_support_ticket_customer (customer_id, id),
    -- 管理端队列按状态筛选并按主键倒序。
    KEY idx_support_ticket_status (status, id),
    -- 管理端按窗口筛选。
    KEY idx_support_ticket_window (window_id, id),
    -- 餐厅经理按餐厅范围筛选。
    KEY idx_support_ticket_restaurant (restaurant_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='学生反馈工单';

-- 工单时间线消息表：学生补充、商家公开回复、内部备注和系统状态消息共用一条时间线。
-- internal_note = 1 的消息只允许管理端读取，学生接口必须在 SQL 层过滤。
CREATE TABLE IF NOT EXISTS support_ticket_message
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '内部顺序主键',
    message_id      CHAR(36)      NOT NULL COMMENT '公开消息标识',
    ticket_id       BIGINT        NOT NULL COMMENT '关联工单内部主键',
    sender_type     VARCHAR(16)   NOT NULL COMMENT '发送方：CUSTOMER/STAFF/SYSTEM',
    sender_id       BIGINT        NULL COMMENT '发送方内部用户编号，系统消息为空',
    content         VARCHAR(1000) NOT NULL COMMENT '消息正文',
    image_file_keys JSON          NOT NULL COMMENT '图片文件键数组，最多 3 个',
    internal_note   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否内部备注：1 学生不可见',
    request_key     CHAR(36)      NULL COMMENT '补充说明幂等键；系统消息为空',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_message_id (message_id),
    -- 同一工单内同一幂等键只允许一条补充说明；系统消息 request_key 为空不受影响。
    UNIQUE KEY uk_support_message_request (ticket_id, request_key),
    -- 时间线按工单内主键正序读取。
    KEY idx_support_message_ticket (ticket_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工单时间线消息';
