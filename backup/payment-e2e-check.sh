#!/usr/bin/env bash
# 核对支付表最终状态
docker exec order-system-cloud-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -e "
USE payment_db;
SELECT payment_id, payment_no, order_id, amount, paid_amount, status, notify_count, channel_trade_no, success_time FROM payments;
USE order_db;
SELECT order_id, status, version FROM orders;
"'
