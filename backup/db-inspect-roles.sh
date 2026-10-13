#!/usr/bin/env bash
# 只读检查：列出各库表、角色/状态相关列，以及 users 表现有 role_code 取值分布。
# 用途：确认代码里的角色编码是否与数据库实际取值一致（数据库为准）。
set -euo pipefail

ENV_FILE=/mnt/e/order_system_cloud/.env
MYSQL_ROOT_PASSWORD=$(grep -E '^MYSQL_ROOT_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '\r')
CONTAINER=order-system-cloud-mysql-1

docker exec "$CONTAINER" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "
SELECT CONCAT('[', table_schema, '] ', table_name)
FROM information_schema.tables
WHERE table_schema IN ('user_db','order_db','product_db','payment_db')
ORDER BY table_schema, table_name;

SELECT '---- 角色/状态相关列 ----';
SELECT CONCAT(table_schema,'.',table_name,'.',column_name,' => ',column_type)
FROM information_schema.columns
WHERE table_schema IN ('user_db','order_db','product_db','payment_db')
  AND (column_name LIKE '%role%' OR column_name LIKE '%status%')
ORDER BY table_schema, table_name;

SELECT '---- users.role_code 取值分布 ----';
SELECT CONCAT(role_code, ' x ', COUNT(*)) FROM user_db.users GROUP BY role_code;
" 2>&1 | grep -v "Using a password"
