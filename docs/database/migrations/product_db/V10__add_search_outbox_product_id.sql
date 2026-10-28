-- 旧窗口事件保留 NULL，新菜品事件记录菜品 ID；旧消息仍可由新消费者处理。
USE product_db;

ALTER TABLE product_search_outbox
    ADD COLUMN product_id BIGINT NULL COMMENT '菜品 ID；为空表示刷新整个窗口' AFTER window_id;
