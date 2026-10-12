#!/usr/bin/env bash
# 支付模块端到端验证：注册→登录→下单→创建支付→模拟渠道支付→验证状态流转
set -e
# WSL2 NAT 模式下 127.0.0.1 是 WSL 自身，Windows 宿主需经默认网关访问
HOST_IP=$(ip route show default | awk '{print $3}')
[ -z "$HOST_IP" ] && HOST_IP=127.0.0.1
GW=http://$HOST_IP:9090
PAY=http://$HOST_IP:9101
USERNAME="payuser$(date +%s)"
PHONE="139$(date +%N | tr -d '\n' | cut -c1-8)"
PASSWORD="Pay123456"
J='Content-Type: application/json'

echo "== 1. 注册测试用户 $USERNAME =="
curl -s -X POST "$GW/api/auth/register" -H "$J" \
  -d "{\"username\":\"$USERNAME\",\"phone\":\"$PHONE\",\"password\":\"$PASSWORD\"}"
echo; echo

echo "== 2. 登录获取 Token =="
LOGIN=$(curl -s -X POST "$GW/api/auth/login" -H "$J" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "token: ${TOKEN:0:40}..."
echo

echo "== 3. 创建订单（窗口1 × 商品1）=="
ORDER=$(curl -s -X POST "$GW/api/orders" -H "$J" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: e2e-pay-$USERNAME" \
  -d '{"windowId":1,"items":[{"productId":1,"quantity":1}],"remark":"支付链路端到端验证"}')
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | sed -n 's/.*"orderId":\([0-9]*\).*/\1/p')
echo "orderId=$ORDER_ID"
echo

echo "== 4. 创建支付单 =="
CREATE=$(curl -s -X POST "$GW/api/payments" -H "$J" \
  -H "Authorization: Bearer $TOKEN" -d "{\"orderId\":$ORDER_ID}")
echo "$CREATE"
PAY_NO=$(echo "$CREATE" | sed -n 's/.*"paymentNo":"\([^"]*\)".*/\1/p')
echo "paymentNo=$PAY_NO"
echo

echo "== 5. 查看模拟收银台 =="
curl -s "$PAY/mock/channel/checkout/$PAY_NO"
echo; echo

echo "== 6. 模拟渠道确认支付成功 =="
curl -s -X POST "$PAY/mock/channel/pay/$PAY_NO" -H "$J" -d '{"outcome":"SUCCESS"}'
echo
sleep 3

echo "== 7. 查询支付状态（期望 SUCCESS）=="
curl -s "$GW/api/payments/order/$ORDER_ID" -H "Authorization: Bearer $TOKEN"
echo; echo

echo "== 8. 查询订单状态（期望已支付）=="
curl -s "$GW/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN"
echo
