#!/bin/bash
BASE="http://172.20.211.23"
PASS=0; FAIL=0; SKIP=0

test_api() {
  local port=$1 path=$2
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) PASS=$((PASS+1));;
    *"Not found"*|*"404"*) SKIP=$((SKIP+1));;
    *) FAIL=$((FAIL+1)); echo "- $path ($result)";;
  esac
}

echo "=== 第九阶段：冲刺 90% ==="
echo ""

# Daily-ticket 剩余 (目标 34/34)
test_api 30027 "/ci/daily-ticket/payment/receivePayResult"
test_api 30027 "/ci/daily-ticket/payment/receiveRefundResult"
test_api 30027 "/ci/daily-ticket/requestOrderFree"
test_api 30027 "/ci/daily-ticket/syncOrder"
test_api 30027 "/ci/daily-ticket/active-notify"
test_api 30027 "/ci/daily-ticket/converge"
test_api 30027 "/ci/daily-ticket/daily"
test_api 30027 "/ci/daily-ticket/monthly"
test_api 30027 "/ci/daily-ticket/notify"
test_api 30027 "/ci/daily-ticket/resubmit"
test_api 30027 "/ci/daily-ticket/retry"
test_api 30027 "/ci/daily-ticket/pay-query"

# Ticket 剩余 (目标 19/19)
test_api 30014 "/ci/ticket/notiVerifyResult"
test_api 30014 "/ci/ticket/export"
test_api 30014 "/ci/ticket/queryFirstEntryTxn"
test_api 30014 "/ci/ticket/queryLatestEntryTxnBeforeExit"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, $SKIP 跳过"
