-- 新服务专用数据库；仅由部署账号执行，不修改既有业务库。
CREATE DATABASE IF NOT EXISTS comment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE comment_db;
CREATE TABLE IF NOT EXISTS comments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部排序主键，不向客户端暴露',
    comment_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外评价UUID，与MongoDB文档标识一致',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '内部评价用户ID，由认证主体确定',
    author_name VARCHAR(64) NOT NULL COMMENT '评价时的用户昵称快照',
    order_id BIGINT UNSIGNED NOT NULL COMMENT '已完成的购买订单ID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '该订单实际购买的菜品ID',
    window_id BIGINT UNSIGNED NOT NULL COMMENT '订单所属窗口ID，来自订单服务',
    score TINYINT UNSIGNED NOT NULL COMMENT '评分，1至5星',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '可见状态，首期1为已发布',
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端稳定幂等请求UUID',
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归一化请求SHA256摘要',
    created_at DATETIME(6) NOT NULL COMMENT '评价提交时间',
    PRIMARY KEY(id), UNIQUE KEY uk_comment_uuid(comment_id),
    UNIQUE KEY uk_comment_purchase(order_id,product_id), UNIQUE KEY uk_comment_request(user_id,request_key),
    KEY idx_comment_product(product_id,status,id), KEY idx_comment_user(user_id,status,id),
    CONSTRAINT ck_comment_score CHECK(score BETWEEN 1 AND 5),
    CONSTRAINT ck_comment_status CHECK(status=1)
) ENGINE=InnoDB COMMENT='评价关系元数据；正文图片标签保存在MongoDB';
CREATE TABLE IF NOT EXISTS comment_rating_stats (
    product_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
    review_count BIGINT NOT NULL COMMENT '已发布评价数量',
    score_total BIGINT NOT NULL COMMENT '已发布评分总和',
    revision BIGINT NOT NULL COMMENT '本菜品评分快照递增版本',
    PRIMARY KEY(product_id),
    CONSTRAINT ck_comment_rating CHECK(review_count>0 AND score_total>=review_count AND score_total<=review_count*5 AND revision>0)
) ENGINE=InnoDB COMMENT='评论服务权威菜品评分汇总';
CREATE TABLE IF NOT EXISTS comment_rating_outbox (
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评分事件UUID',
    product_id BIGINT UNSIGNED NOT NULL COMMENT '菜品ID',
    revision BIGINT NOT NULL COMMENT '对应权威评分版本',
    payload JSON NOT NULL COMMENT '全量评分事件快照，不含用户和评论正文',
    attempts INT NOT NULL DEFAULT 0 COMMENT '发布失败次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下一次允许发送时间',
    published_at DATETIME NULL COMMENT 'Broker确认发送成功时间，不代表已消费',
    last_error VARCHAR(100) NULL COMMENT '最近失败异常类型，不存凭据和正文',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件创建时间',
    PRIMARY KEY(event_id), UNIQUE KEY uk_comment_rating_version(product_id,revision),
    KEY idx_comment_outbox_pending(published_at,next_attempt_at)
) ENGINE=InnoDB COMMENT='评论评分本地可靠待发送事件';
