-- MySQL 8；不从内部主键推导用户号。保留历史 phone 列，不再参与注册、资料或登录。
-- 已有邮箱为空的旧账号保持 NULL，可使用补发的用户号登录。
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_user_number_email$$
CREATE PROCEDURE migrate_user_number_email()
BEGIN
    DECLARE done BOOLEAN DEFAULT FALSE;
    DECLARE current_id BIGINT UNSIGNED;
    DECLARE candidate CHAR(10);
    DECLARE pending CURSOR FOR SELECT user_id FROM users WHERE user_no IS NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email') THEN
        ALTER TABLE users ADD COLUMN email VARCHAR(254) NULL COMMENT '登录邮箱';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'user_no') THEN
        ALTER TABLE users ADD COLUMN user_no CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '永久登录用户号';
    END IF;
    ALTER TABLE users MODIFY email VARCHAR(254) NULL COMMENT '登录邮箱',
                      MODIFY username VARCHAR(64) NOT NULL;
    UPDATE users SET email = NULLIF(LOWER(TRIM(email)), ''), updated_at = updated_at;
    IF EXISTS (SELECT email FROM users WHERE email IS NOT NULL GROUP BY email HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate email: resolve ownership before migration';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_email') THEN
        ALTER TABLE users ADD UNIQUE KEY uk_users_email (email);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'uk_users_user_no') THEN
        ALTER TABLE users ADD UNIQUE KEY uk_users_user_no (user_no);
    END IF;

    OPEN pending;
    allocate_loop: LOOP
        FETCH pending INTO current_id;
        IF done THEN LEAVE allocate_loop; END IF;
        SET candidate = CAST(FLOOR(1000000000 + RAND() * 9000000000) AS CHAR);
        WHILE EXISTS (SELECT 1 FROM users WHERE user_no = candidate) DO
            SET candidate = CAST(FLOOR(1000000000 + RAND() * 9000000000) AS CHAR);
        END WHILE;
        UPDATE users SET user_no = candidate, updated_at = updated_at WHERE user_id = current_id;
    END LOOP;
    CLOSE pending;
    ALTER TABLE users MODIFY user_no CHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '永久登录用户号';
END$$
DELIMITER ;
CALL migrate_user_number_email();
DROP PROCEDURE migrate_user_number_email;

-- 在数据库管理工具中查看分配结果；不要把含邮箱的导出提交到仓库。
-- SELECT username, user_no, email FROM users WHERE deleted_at IS NULL;
