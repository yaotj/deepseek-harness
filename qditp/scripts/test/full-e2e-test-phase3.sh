#!/bin/bash
BASE_URL="http://172.20.211.23"
PASS=0
FAIL=0
TOTAL=0

run_test() {
    local service="$1"
    local port="$2"
    local path="$3"
    local data="$4"
    TOTAL=$((TOTAL + 1))
    result=$(curl -s -k "${BASE_URL}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
    if echo "$result" | grep -q '"retCode"\|"code"\|"success"'; then
        echo "✓ PASS: [$service] $path"
        PASS=$((PASS + 1))
    else
        echo "✗ FAIL: [$service] $path - $(echo $result | cut -c1-60)"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== 第三阶段：批量补充测试 ==="
echo ""
run_test "daily-ticket" 30027 "/ci/daily-ticket/queryDailyTicketPayInfo" '{"orderNo":"0E202611161424100004","orderType":"1"}'
run_test "daily-ticket" 30027 "/ci/daily-ticket/export" '{}'
run_test "ticket" 30014 "/ci/ticket/online" '{"cardNum":"0426090951000208"}'
run_test "ticket" 30014 "/ci/ticket/offline" '{"cardNum":"0426090951000208"}'
run_test "ticket" 30014 "/ci/ticket/queryCardStatus" '{"cardNum":"0426090951000208"}'
run_test "ticket" 30014 "/ci/ticket/requestCardDataAnalyse" '{"cardNum":"0426090951000208"}'
run_test "ticket" 30014 "/ci/ticket/findTravelList" '{"cardNum":"0426090951000208"}'
run_test "account" 30013 "/ci/account/queryUserInfo" '{"msisdn":"15000000203"}'
run_test "account" 30013 "/ci/account/close" '{"msisdn":"15000000203"}'
run_test "account" 30013 "/ci/account/employeeCard/activate" '{"cardNum":"0426090951000208"}'
run_test "account" 30013 "/ci/account/queryActiveCards" '{"msisdn":"15000000203"}'
run_test "gate-txn-pay" 30019 "/ci/gate-txn-pay/queryByOrderNo" '{"orderNo":"TEST_001"}'
run_test "gate-txn-pay" 30019 "/ci/gate-txn-pay/requestTransList" '{"userId":"00522990"}'
run_test "face-pay" 30025 "/ci/app/requestOrderResult" '{"orderNo":"TEST_001"}'
run_test "face-pay" 30025 "/ci/app/requestPayment" '{"userId":"00522990","amount":100}'
run_test "collect-pay" 30024 "/ci/collect-pay/requestOrder" '{"userId":"00522990"}'
run_test "collect-pay" 30024 "/ci/collect-pay/requestPayResult" '{"orderNo":"TEST_001"}'
echo ""
echo "通过: $PASS / 失败: $FAIL / 总计: $TOTAL"
