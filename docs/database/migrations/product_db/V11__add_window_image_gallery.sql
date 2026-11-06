-- 执行前备份product_db.dining_windows并确认尚未存在image_file_keys；本脚本只执行一次。
-- 保留原封面列兼容首页与搜索，文件内容仍由文件服务管理，业务库仅保存有序文件键。
USE product_db;
ALTER TABLE dining_windows
    ADD COLUMN image_file_keys JSON NULL COMMENT '店铺有序图片文件键数组，最多5张，首图为封面' AFTER image_file_key,
    ADD CONSTRAINT chk_window_gallery_size CHECK (
        image_file_keys IS NULL OR (JSON_TYPE(image_file_keys) = 'ARRAY' AND JSON_LENGTH(image_file_keys) <= 5)
    );

-- 旧封面原样保留为第一张；没有封面的窗口初始化为空数组，不生成虚假文件引用。
UPDATE dining_windows SET image_file_keys = CASE
    WHEN image_file_key IS NULL OR image_file_key = '' THEN JSON_ARRAY()
    ELSE JSON_ARRAY(image_file_key)
END;

-- 发布顺序：迁移成功后更新商品服务，再更新学生端；旧服务继续写单列会造成列表不一致，避免混跑。
-- 验收：SELECT window_id,image_file_key,image_file_keys FROM dining_windows;
