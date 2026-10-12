#!/usr/bin/env bash
# 订单1补偿推进（首次 e2e 时 Feign PATCH 失败遗留）+ 回调重放幂等验证
set -e
HOST_IP=$(ip route show default | awk '{print $3}')
PAY=http://$HOST_IP:9101
TOKEN=internal_order_token_dev_2026
SECRET=mock_channel_secret_dev_2026

echo "== A. 直接调用订单内部接口推进订单1（待支付→已支付）=="
curl -s -X PATCH "http://$HOST_IP:9091/internal/orders/1/paid" -H "X-Internal-Token: $TOKEN"
echo; echo

echo "== B. 重放支付1的成功回调（验证幂等，notify_count 应 +1）=="
PAY_NO=PAY202610121626041285F6CC
TRADE_NO=MOCK20261012162605068A80F
TS=$(date +%s%3N)
SIG=$(printf '%s\n%s\n%s\n%s\n%s' "$PAY_NO" "$TRADE_NO" "18.50" "SUCCESS" "$TS" \
    | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')
curl -s -X POST "$PAY/internal/payments/notify" \
    -H "Content-Type: application/json" -H "X-Channel-Signature: $SIG" \
    -d "{\"paymentNo\":\"$PAY_NO\",\"channelTradeNo\":\"$TRADE_NO\",\"paidAmount\":\"18.50\",\"status\":\"SUCCESS\",\"timestamp\":$TS}"
echo; echo

echo "== C. 篡改签名重放（应返回 401 验签失败）=="
curl -s -X POST "$PAY/internal/payments/notify" \
    -H "Content-Type: application/json" -H "X-Channel-Signature: deadbeef" \
    -d "{\"paymentNo\":\"$PAY_NO\",\"channelTradeNo\":\"$TRADE_NO\",\"paidAmount\":\"18.50\",\"status\":\"SUCCESS\",\"timestamp\":$TS}"
echo
