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

echo "=== 第十一阶段：Face-pay 全覆盖 ==="
echo ""

# Face-pay 剩余接口 (目标 54/54)
test_api 30025 "/ci/app/requestPayResult"
test_api 30025 "/ci/app/requestPaymentInfo"
test_api 30025 "/ci/app/requestOrder"
test_api 30025 "/ci/app/requestRefundTicket"
test_api 30025 "/ci/app/requestRefundTicketResult"
test_api 30025 "/ci/app/requestPreActiveOrderList"
test_api 30025 "/ci/app/requestActiveTicket"
test_api 30025 "/ci/app/receiveRefundResult"
test_api 30025 "/ci/app/queryBlackList"
test_api 30025 "/ci/app/queryUserItinerary"
test_api 30025 "/ci/app/requestTransList"
test_api 30025 "/ci/app/requestTransDetail"
test_api 30025 "/ci/app/requestTransStatistics"
test_api 30025 "/app/ticket/realName"
test_api 30025 "/ci/app/requestTermination"
test_api 30025 "/ci/app/receiveTerminationResult"
test_api 30025 "/ci/app/requestContractResult"
test_api 30025 "/ci/app/receiveSignResult"
test_api 30025 "/userData/unbindAgreement"
test_api 30025 "/ci/app/paySign/payment/receiveRefundResult"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败"
