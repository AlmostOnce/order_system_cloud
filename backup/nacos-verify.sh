#!/usr/bin/env bash
# 验证 Nacos 管理员与网关配置在端口迁移后仍然完好
resp=$(curl -s -X POST "http://127.0.0.1:9097/nacos/v3/auth/user/login" -d "username=nacos&password=root")
echo "login: ${resp:0:80}"
token=$(echo "$resp" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "config readback:"
curl -s "http://127.0.0.1:9097/nacos/v3/admin/cs/config?accessToken=$token&dataId=gateway-service.yaml&groupName=DEFAULT_GROUP" | head -c 200
