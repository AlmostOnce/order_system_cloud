-- 推荐收藏事件发件箱：只新增表，不改变已有收藏关系；执行前核对目标库与版本。
USE user_db;

CREATE TABLE IF NOT EXISTS user_favorite_event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件全局递增编号；同一收藏关系的数值顺序用于拒绝旧事件',
    user_id BIGINT NOT NULL COMMENT '收藏所属用户内部编号，仅供授权内部消费者使用',
    target_type VARCHAR(16) NOT NULL COMMENT '收藏目标类型：PRODUCT菜品或WINDOW店铺',
    target_id BIGINT NOT NULL COMMENT '商品服务中的菜品或窗口编号',
    active BOOLEAN NOT NULL COMMENT '真实新增为1，真实删除为0；删除事件是防止旧收藏复活的墓碑',
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '用户库事务登记变更时间，不作为事件顺序依据',
    attempts INT NOT NULL DEFAULT 0 COMMENT '向Broker发布失败次数',
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '允许再次发布的时间',
    published_at DATETIME(3) NULL DEFAULT NULL COMMENT 'Broker同步确认接收的时间，空表示仍需重试',
    last_error VARCHAR(128) NULL DEFAULT NULL COMMENT '最近一次发布异常类型，不保存个人资料和异常详情',
    PRIMARY KEY (id),
    KEY idx_favorite_outbox_pending (published_at, next_attempt_at, id),
    KEY idx_favorite_outbox_target (user_id, target_type, target_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='学生收藏变更推荐事件发件箱';
