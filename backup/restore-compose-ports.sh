#!/usr/bin/env bash
# 端口迁移后按 docker-compose.yml 重建基础设施容器（数据卷保留，严禁使用 -v）
set -e
cd /mnt/e/order_system_cloud
docker compose config --quiet && echo "== compose 配置校验通过 =="
docker compose up -d mysql redis nacos
echo "== 等待容器就绪 =="
sleep 8
docker compose ps
echo "== 端口监听确认 =="
ss -tlnp 2>/dev/null | grep -E ':(9095|9096|9097|9098)\b' || netstat -tln 2>/dev/null | grep -E ':(9095|9096|9097|9098)\b' || echo "（无 ss/netstat 输出，跳过）"
