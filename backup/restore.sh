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

# 旧备份中的商品演示数据是二次编码的乱码，恢复后立即修复中文，避免问题回滚。
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4' < docs/database/seeds/product_catalog_encoding_repair.sql
echo "product catalog encoding repair done"

docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema IN (\"order_db\",\"user_db\",\"product_db\") GROUP BY table_schema"'
