#!/usr/bin/env bash
set -e
cd /mnt/e/order_system_cloud
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT user, host FROM mysql.user WHERE user IN (\"order_user\",\"product_service\",\"user_service\"); SELECT \"--- grants ---\"; SHOW GRANTS FOR \"user_service\"@\"%\"; SHOW GRANTS FOR \"product_service\"@\"%\"; SHOW GRANTS FOR \"order_user\"@\"%\""' 2>/dev/null
