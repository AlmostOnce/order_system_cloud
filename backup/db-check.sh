#!/usr/bin/env bash
set -e
cd /mnt/e/order_system_cloud
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SHOW DATABASES; SELECT \"---\"; SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN (\"mysql\",\"sys\",\"information_schema\",\"performance_schema\")"' 2>/dev/null
