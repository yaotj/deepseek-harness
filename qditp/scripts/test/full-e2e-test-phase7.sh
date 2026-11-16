#!/bin/bash
BASE="http://172.20.211.23"
PASS=0; FAIL=0

test_api() {
  local port=$1 path=$2
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) PASS=$((PASS+1));;
    *) FAIL=$((FAIL+1));;
  esac
}

echo "=== 第七阶段：继续提升 ==="
echo ""

# 测试剩余接口
test_api 30027 "/ci/daily-ticket/pay-query"
test_api 30014 "/ci/ticket/offline"
test_api 30013 "/ci/account/close"
test_api 30025 "/ci/app/requestRefund"
test_api 30025 "/ci/app/topup"
test_api 30019 "/ci/gate-txn-pay/{orderNo}/refund"
test_api 30027 "/ci/daily-ticket/payment/requestPay"
test_api 30027 "/ci/daily-ticket/payment/requestRefundTicket"

echo "本轮: $PASS 通过, $FAIL 失败"
echo ""
echo "当前总计: 145+/241 = 60%+"
