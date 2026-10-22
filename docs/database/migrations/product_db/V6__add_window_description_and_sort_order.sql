USE product_db;

-- 窗口简介与窗口内展示排序。存量窗口默认排序为 0，相同排序按窗口 ID 升序。
-- 本迁移仅执行一次；执行前确认两列尚不存在。
ALTER TABLE dining_windows
    ADD COLUMN description VARCHAR(500) NULL COMMENT '窗口简介' AFTER window_name,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序，越小越靠前' AFTER is_open,
    ADD CONSTRAINT chk_dining_windows_sort_order CHECK (sort_order >= 0);
