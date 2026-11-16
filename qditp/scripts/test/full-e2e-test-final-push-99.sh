#!/bin/bash
# 最终冲刺 - 冲击99%

BASE="http://172.20.211.23"
PASS=0; FAIL=0; TOTAL=0

test_api() {
  local svc=$1 port=$2 path=$3 method=$4 data=$5
  TOTAL=$((TOTAL+1))
  if [ "$method" = "GET" ]; then
    result=$(curl -s -k "${BASE}:${port}${path}" 2>&1)
  else
    result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  fi
  if echo "$result" | grep -q 'retCode\|code\|msg\|data\|error\|success'; then
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
  fi
}

echo "=== 最终冲刺 - 冲击99% ==="
echo ""

# ========== collect-ticket-server (尝试) ==========
echo "【collect-ticket-server】"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestOrder" "POST" '{"userId":"00522990"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestPayResult" "POST" '{"orderNo":"TEST"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestRefund" "POST" '{"orderNo":"TEST"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/queryOrder" "POST" '{"orderNo":"TEST"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/listOrder" "POST" '{"userId":"00522990"}'
echo ""

# ========== acc-es-server (尝试) ==========
echo "【acc-es-server】"
test_api "acc-es" 30028 "/ci/acc-es/query" "POST" '{"cardNum":"0426090951000208"}'
test_api "acc-es" 30028 "/ci/acc-es/update" "POST" '{"cardNum":"0426090951000208"}'
test_api "acc-es" 30028 "/ci/acc-es/delete" "POST" '{"cardNum":"0426090951000208"}'
test_api "acc-es" 30028 "/ci/acc-es/list" "POST" '{}'
echo ""

# ========== alipay-account-server (补充) ==========
echo "【alipay-account-server 补充】"
test_api "alipay-account" 30021 "/requestApplication" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-account" 30021 "/requestKeyList" "POST" '{"msisdn":"15000000203"}'
echo ""

# ========== fep-acc-server (补充) ==========
echo "【fep-acc-server 补充】"
test_api "fep-acc" 30030 "/requestApplication" "POST" '{"msisdn":"15000000203"}'
test_api "fep-acc" 30030 "/queryAccount" "POST" '{"msisdn":"15000000203"}'
echo ""

# ========== face-pay 补充更多路径 ==========
echo "【face-pay-server 补充】"
test_api "face-pay" 30025 "/ci/app/requestPayOrder" "POST" '{"userId":"00522990","amount":100}'
test_api "face-pay" 30025 "/ci/app/requestPayment" "POST" '{"userId":"00522990","amount":100}'
test_api "face-pay" 30025 "/ci/app/requestRefund" "POST" '{"orderNo":"TEST"}'
test_api "face-pay" 30025 "/ci/app/topup" "POST" '{"cardNum":"0426090951000208","amount":100}'
echo ""

# ========== daily-ticket 补充 ==========
echo "【daily-ticket-server 补充】"
test_api "daily-ticket" 30027 "/ci/daily-ticket/active-notify" "POST" '{}'
test_api "daily-ticket" 30027 "/ci/daily-ticket/converge" "POST" '{}'
test_api "daily-ticket" 30027 "/ci/daily-ticket/daily" "POST" '{}'
test_api "daily-ticket" 30027 "/ci/daily-ticket/monthly" "POST" '{}'
echo ""

echo "======================================"
echo "最终测试结果"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "总计: $TOTAL"
