-- 保留 user_id 主键和全部业务关联；为存量与新账号生成稳定随机 UUID v4。
-- MySQL >= 8.0.13，表达式默认值保证旧写入流程也不会产生空 UUID。
ALTER TABLE users ADD COLUMN user_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER user_id;
UPDATE users SET user_uuid = LOWER(CONCAT(HEX(RANDOM_BYTES(4)), '-', HEX(RANDOM_BYTES(2)), '-4', SUBSTRING(HEX(RANDOM_BYTES(2)), 2), '-', ELT(1 + (ORD(RANDOM_BYTES(1)) & 3), '8', '9', 'a', 'b'), SUBSTRING(HEX(RANDOM_BYTES(2)), 2), '-', HEX(RANDOM_BYTES(6)))), updated_at = updated_at WHERE user_uuid IS NULL;
ALTER TABLE users
    MODIFY user_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT (LOWER(CONCAT(HEX(RANDOM_BYTES(4)), '-', HEX(RANDOM_BYTES(2)), '-4', SUBSTRING(HEX(RANDOM_BYTES(2)), 2), '-', ELT(1 + (ORD(RANDOM_BYTES(1)) & 3), '8', '9', 'a', 'b'), SUBSTRING(HEX(RANDOM_BYTES(2)), 2), '-', HEX(RANDOM_BYTES(6))))) COMMENT '对外稳定随机 UUID v4',
    ADD UNIQUE KEY uk_users_user_uuid (user_uuid);
