#!/usr/bin/env bash
# 重建 user_service / product_service 账号（引擎迁移后 mysql 系统库账号丢失）
# 密码与 user-service / product-service 的 application.yml 保持一致
set -e
cd /mnt/e/order_system_cloud
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
CREATE USER IF NOT EXISTS \"user_service\"@\"%\" IDENTIFIED BY \"user_password_dev_2026\";
GRANT ALL PRIVILEGES ON user_db.* TO \"user_service\"@\"%\";
CREATE USER IF NOT EXISTS \"product_service\"@\"%\" IDENTIFIED BY \"product_password_dev_2026\";
GRANT ALL PRIVILEGES ON product_db.* TO \"product_service\"@\"%\";
SELECT user, host FROM mysql.user WHERE user IN (\"order_user\",\"product_service\",\"user_service\");
SHOW GRANTS FOR \"user_service\"@\"%\";
SHOW GRANTS FOR \"product_service\"@\"%\";
"' 2>/dev/null
