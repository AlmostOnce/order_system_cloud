#!/usr/bin/env bash
# 创建 payment_db 数据库、payments 表与 payment_service 账号（引擎重建后可重复执行）
# 迁移 SQL：docs/database/migrations/payment_db/V1__create_payments_table.sql
set -e
docker exec -i order-system-cloud-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4' \
    < /mnt/e/order_system_cloud/docs/database/migrations/payment_db/V1__create_payments_table.sql
docker exec order-system-cloud-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
USE payment_db;
SHOW TABLES;
SELECT user, host FROM mysql.user WHERE user = \"payment_service\";
SHOW GRANTS FOR \"payment_service\"@\"%\";
"'
