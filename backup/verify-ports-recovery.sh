#!/usr/bin/env bash
# 端口恢复后的连通性与数据完整性验证
set -e
echo "== 1. Nacos API (9097) =="
resp=$(curl -s -X POST "http://127.0.0.1:9097/nacos/v3/auth/user/login" -d "username=nacos&password=root")
echo "login: ${resp:0:60}"
token=$(echo "$resp" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "网关路由读取（应含 payment-service）:"
curl -s "http://127.0.0.1:9097/nacos/v3/admin/cs/config?accessToken=$token&dataId=gateway-service.yaml&groupName=DEFAULT_GROUP" | grep -c "payment-service"

echo "== 2. Nacos 控制台 (9098) =="
curl -s -o /dev/null -w "console http_code: %{http_code}\n" "http://127.0.0.1:9098/"

echo "== 3. Redis (9096) =="
docker exec order-system-cloud-redis-1 redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null || \
docker exec order-system-cloud-redis-1 sh -c 'redis-cli -a "$REDIS_PASSWORD" ping'

echo "== 4. MySQL (9095) 数据完整性 =="
docker exec order-system-cloud-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "
SELECT \"服务账号数(应为3):\", COUNT(*) FROM mysql.user WHERE user IN (\"user_service\",\"product_service\",\"payment_service\");
SELECT \"订单数:\", COUNT(*) FROM order_db.orders;
SELECT \"支付单数:\", COUNT(*) FROM payment_db.payments;
SELECT \"商品种子数:\", COUNT(*) FROM product_db.products;"' 2>/dev/null | grep -v "Warning"
echo "== 验证完成 =="
