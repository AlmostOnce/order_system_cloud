#!/usr/bin/env bash
# 验证 SYSTEM_ADMIN 引导落库结果
set -e
docker exec order-system-cloud-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT user_id, username, role_code, status, version, LEFT(password_hash,7) AS hash_prefix FROM user_db.users ORDER BY user_id;"'
