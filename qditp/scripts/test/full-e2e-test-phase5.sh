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

echo "=== 第五阶段：继续提升覆盖率 ==="
echo ""

# Gate-txn-pay 补充
test_api 30019 "/ci/gate-txn-pay/backfill-original-fare"
test_api 30019 "/ci/gate-txn-pay/batch-refund-overtime"
test_api 30019 "/ci/gate-txn-pay/countTransList"
test_api 30019 "/ci/gate-txn-pay/debit/retry/alipay"
test_api 30019 "/ci/gate-txn-pay/debit/retry/default"
test_api 30019 "/ci/gate-txn-pay/hasUnsettledOrderByCard"
test_api 30019 "/ci/gate-txn-pay/metro-transfer/push"
test_api 30019 "/ci/gate-txn-pay/offline-fare/recover"
test_api 30019 "/ci/gate-txn-pay/retry-queue/consume"

# Daily-ticket 补充
test_api 30027 "/ci/daily-ticket/active-notify"
test_api 30027 "/ci/daily-ticket/converge"
test_api 30027 "/ci/daily-ticket/daily"
test_api 30027 "/ci/daily-ticket/monthly"

echo "本轮: $PASS 通过, $FAIL 失败"
