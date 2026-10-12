#!/usr/bin/env bash
# 发布 Gateway 路由配置到 Nacos（新增 payment-service 路由后使用）
# 配置基准文件：docs/nacos/gateway-service.yaml
set -e
resp=$(curl -s -X POST "http://127.0.0.1:9097/nacos/v3/auth/user/login" \
    -d "username=nacos&password=root")
token=$(echo "$resp" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -z "$token" ]; then
    echo "Nacos 登录失败: $resp"
    exit 1
fi
curl -s -X POST "http://127.0.0.1:9097/nacos/v3/admin/cs/config" \
    --data-urlencode "dataId=gateway-service.yaml" \
    --data-urlencode "groupName=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=public" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@/mnt/e/order_system_cloud/docs/nacos/gateway-service.yaml" \
    --data-urlencode "accessToken=$token"
echo ""
echo "发布完成，回读验证 payment 路由："
curl -s "http://127.0.0.1:9097/nacos/v3/admin/cs/config?accessToken=$token&dataId=gateway-service.yaml&groupName=DEFAULT_GROUP" \
    | grep -c "payment-service"
