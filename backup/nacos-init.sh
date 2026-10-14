#!/usr/bin/env bash
# Nacos v3 初始化：创建 nacos/root 管理员 + 发布网关路由配置
set -e
NACOS=http://127.0.0.1:9097/nacos

echo "== 0. 等待 Nacos 就绪 =="
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$NACOS/v3/console/server/state" || true)
  [ "$code" = "200" ] && break
  sleep 5
done
echo "nacos http: $code"

echo "== 1. 初始化管理员 nacos/root =="
curl -s -X POST "$NACOS/v3/auth/user/admin" -d "username=nacos&password=root"
echo ""

echo "== 2. 登录获取 accessToken =="
resp=$(curl -s -X POST "$NACOS/v3/auth/user/login" -d "username=nacos&password=root")
echo "$resp"
token=$(echo "$resp" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

echo "== 3. 发布 gateway-service.yaml =="
curl -s -X POST "$NACOS/v3/admin/cs/config" \
  -d "accessToken=$token" \
  --data-urlencode "dataId=gateway-service.yaml" \
  -d "groupName=DEFAULT_GROUP" \
  -d "type=yaml" \
  --data-urlencode "content@/mnt/e/order_system_cloud/docs/nacos/gateway-service.yaml"
echo ""

echo "== 4. 发布 product-service-degrade-rules（Sentinel 降级规则） =="
curl -s -X POST "$NACOS/v3/admin/cs/config" \
  -d "accessToken=$token" \
  --data-urlencode "dataId=product-service-degrade-rules" \
  -d "groupName=SENTINEL_GROUP" \
  -d "type=json" \
  --data-urlencode "content@/mnt/e/order_system_cloud/docs/nacos/product-service-degrade-rules"
echo ""

echo "== 5. 回读验证 =="
curl -s "$NACOS/v3/admin/cs/config?accessToken=$token&dataId=gateway-service.yaml&groupName=DEFAULT_GROUP" | head -c 400
echo ""
curl -s "$NACOS/v3/admin/cs/config?accessToken=$token&dataId=product-service-degrade-rules&groupName=SENTINEL_GROUP" | head -c 400
