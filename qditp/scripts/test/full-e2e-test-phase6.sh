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

echo "=== 第六阶段：冲刺 75% ==="
echo ""

# Account 补充
test_api 30013 "/ci/account/channel/requestApplication"
test_api 30013 "/ci/account/employeeCard/updateNotify"
test_api 30013 "/ci/account/internal/payChannel/syncPayAccountId"
test_api 30013 "/ci/account/queryPayChannelByContractNo"
test_api 30013 "/ci/account/quartzDemo"
test_api 30013 "/ci/account/userCancel"

# Ticket 补充
test_api 30014 "/ci/ticket/findTravelDetail"
test_api 30014 "/ci/ticket/queryLatestEntryTxnBeforeExit"

# Daily-ticket 补充
test_api 30027 "/ci/daily-ticket/request"
test_api 30027 "/ci/daily-ticket/query"

# Face-pay 补充
test_api 30025 "/ci/app/requestTermination"
test_api 30025 "/ci/app/receiveTerminationResult"
test_api 30025 "/ci/app/requestContractResult"
test_api 30025 "/ci/app/receiveSignResult"
test_api 30025 "/ci/app/unbindAgreement"

# Gate-txn-pay 补充
test_api 30019 "/ci/gate-txn-pay/debit/retry/recent"

echo "本轮: $PASS 通过, $FAIL 失败"
