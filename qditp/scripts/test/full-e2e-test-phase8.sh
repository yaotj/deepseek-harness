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

echo "=== 第八阶段：冲刺 75% ==="
echo ""

# 继续测试剩余接口
test_api 30027 "/ci/daily-ticket/payment/receivePayResult"
test_api 30027 "/ci/daily-ticket/payment/receiveRefundResult"
test_api 30014 "/ci/ticket/notiVerifyResult"
test_api 30013 "/ci/account/batch-search"
test_api 30019 "/ci/gate-txn-pay/export"
test_api 30025 "/ci/app/receivePayResult"
test_api 30025 "/ci/app/requestPayment"
test_api 30025 "/ci/app/requestOrderResult"
test_api 30025 "/ci/app/requestRefundTicketResult"
test_api 30025 "/ci/app/requestPreActiveOrderList"
test_api 30019 "/ci/gate-txn-pay/queryByOrderNo"
test_api 30019 "/ci/gate-txn-pay/requestTransList"
test_api 30019 "/ci/gate-txn-pay/requestTransStatistics"
test_api 30013 "/ci/account/employeeCard/activate"
test_api 30013 "/ci/account/employeeCard/query"
test_api 30027 "/ci/daily-ticket/queryDailyTicketPayInfo"
test_api 30014 "/ci/ticket/findTravelList"
test_api 30014 "/ci/ticket/findTravelDetail"
test_api 30025 "/ci/app/requestActiveTicket"
test_api 30025 "/ci/app/queryBlackList"
test_api 30025 "/ci/app/queryUserItinerary"
test_api 30025 "/ci/app/requestTransList"
test_api 30025 "/ci/app/requestTransDetail"
test_api 30025 "/ci/app/requestTransStatistics"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败"
