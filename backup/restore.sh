#!/usr/bin/env bash
# 一次性恢复脚本：等待 MySQL 健康 -> 恢复三库 -> 输出表数量
set -e
cd /mnt/e/order_system_cloud

for i in $(seq 1 60); do
  s=$(docker inspect --format '{{.State.Health.Status}}' order-system-cloud-mysql-1)
  [ "$s" = "healthy" ] && break
  sleep 5
done
echo "mysql health: $s"

docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4' < backup/mysql-app-dbs-2026-10-12.sql
echo "restore done"

docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema IN (\"order_db\",\"user_db\",\"product_db\") GROUP BY table_schema"'
