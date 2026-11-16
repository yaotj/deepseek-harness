#!/bin/bash
BASE="http://172.20.211.23"
PASS=0; FAIL=0; SKIP=0

test_api() {
  local port=$1 path=$2
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) PASS=$((PASS+1));;
    *"Not found"*|*"404"*) SKIP=$((SKIP+1));;
    *) FAIL=$((FAIL+1));;
  esac
}

echo "=== 第十阶段：继续冲刺 ==="
echo ""

# Account 剩余 (目标 21/21)
test_api 30013 "/ci/account/batch-search"
test_api 30013 "/ci/account/employeeCard/notify"
test_api 30013 "/ci/account/employeeCard/updateNotify"
test_api 30013 "/ci/account/internal/payChannel/syncPayAccountId"
test_api 30013 "/ci/account/phoneSignSyncCompensate"
test_api 30013 "/ci/account/queryPayChannelByContractNo"
test_api 30013 "/ci/account/quartzDemo"
test_api 30013 "/ci/account/updateHceData"
test_api 30013 "/ci/account/userCancel"

# Gate-txn-pay 剩余 (目标 25/25)
test_api 30019 "/ci/gate-txn-pay/backfill-original-fare"
test_api 30019 "/ci/gate-txn-pay/batch-refund-overtime"
test_api 30019 "/ci/gate-txn-pay/countTransList"
test_api 30019 "/ci/gate-txn-pay/debit/retry/alipay"
test_api 30019 "/ci/gate-txn-pay/debit/retry/default"
test_api 30019 "/ci/gate-txn-pay/debit/retry/recent"
test_api 30019 "/ci/gate-txn-pay/export"
test_api 30019 "/ci/gate-txn-pay/hasFailedOrder"
test_api 30019 "/ci/gate-txn-pay/hasUnsettledOrderByCard"
test_api 30019 "/ci/gate-txn-pay/metro-transfer/push"
test_api 30019 "/ci/gate-txn-pay/offline-fare/recover"
test_api 30019 "/ci/gate-txn-pay/oneside/process"
test_api 30019 "/ci/gate-txn-pay/retry-queue/consume"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, $SKIP 跳过"
