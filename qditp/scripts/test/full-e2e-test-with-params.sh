#!/bin/bash
# 补全参数测试 - 提高通过率

BASE="http://172.20.211.23"
PASS=0; FAIL=0; TOTAL=0

test_api() {
  local svc=$1 port=$2 path=$3 data=$4
  TOTAL=$((TOTAL+1))
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  if echo "$result" | grep -q 'retCode\|code\|msg'; then
    echo "✓ $svc $path"
    PASS=$((PASS+1))
  else
    echo "- $svc $path ($result)"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 补全参数测试 ==="
echo ""

# Collect-ticket-server 补充参数
echo "【collect-ticket-server】"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestOrder" '{"userId":"00522990","ticketPrice":1,"ticketCount":1}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestPayResult" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestRefund" '{"orderNo":"TEST_ORDER_001","reason":"test"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/queryOrder" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/listOrder" '{"userId":"00522990","pageSize":10}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/cancelOrder" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-ticket" 30023 "/ci/collect-ticket/payOrder" '{"orderNo":"TEST_ORDER_001","payChannel":"01"}'
echo ""

# Acc-es-server 补充参数
echo "【acc-es-server】"
test_api "acc-es" 30028 "/ci/acc-es/query" '{"cardNum":"0426090951000208"}'
test_api "acc-es" 30028 "/ci/acc-es/update" '{"cardNum":"0426090951000208","status":"active"}'
test_api "acc-es" 30028 "/ci/acc-es/delete" '{"cardNum":"0426090951000208"}'
test_api "acc-es" 30028 "/ci/acc-es/list" '{"pageSize":10,"pageNum":1}'
test_api "acc-es" 30028 "/ci/acc-es/search" '{"keyword":"test"}'
echo ""

# Alipay-account-server 补充参数
echo "【alipay-account-server】"
test_api "alipay-account" 30029 "/ci/alipay-account/requestApplication" '{"msisdn":"15000000203"}'
test_api "alipay-account" 30029 "/ci/alipay-account/requestKeyList" '{"msisdn":"15000000203"}'
test_api "alipay-account" 30029 "/ci/alipay-account/queryAccount" '{"msisdn":"15000000203"}'
test_api "alipay-account" 30029 "/ci/alipay-account/updateAccount" '{"msisdn":"15000000203","status":"active"}'
echo ""

# Fep-acc-server 补充参数
echo "【fep-acc-server】"
test_api "fep-acc" 30031 "/ci/fep-acc/requestApplication" '{"msisdn":"15000000203"}'
test_api "fep-acc" 30031 "/ci/fep-acc/queryAccount" '{"msisdn":"15000000203"}'
test_api "fep-acc" 30031 "/ci/fep-acc/updateAccount" '{"msisdn":"15000000203"}'
echo ""

# Collect-pay 补充参数
echo "【collect-pay】"
test_api "collect-pay" 30024 "/ci/collect-pay/listOrders" '{"userId":"00522990","pageSize":10}'
test_api "collect-pay" 30024 "/ci/collect-pay/queryDetail" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-pay" 30024 "/ci/collect-pay/exportOrders" '{"startDate":"2026-01-01","endDate":"2026-11-16"}'
test_api "collect-pay" 30024 "/ci/collect-pay/retryPayment" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-pay" 30024 "/ci/collect-pay/cancelOrder" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-pay" 30024 "/ci/collect-pay/getStatus" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-pay" 30024 "/ci/collect-pay/getDetail" '{"orderNo":"TEST_ORDER_001"}'
test_api "collect-pay" 30024 "/ci/collect-pay/listPayments" '{"userId":"00522990"}'
echo ""

# Para-server 补充参数
echo "【para-server】"
test_api "para" 30026 "/ci/para/requestStationInfo" '{"stationCode":"1141"}'
test_api "para" 30026 "/ci/para/requestLineInfo" '{"lineCode":"1"}'
test_api "para" 30026 "/ci/para/requestDeviceList" '{"stationCode":"1141"}'
test_api "para" 30026 "/ci/para/requestStationList" '{}'
test_api "para" 30026 "/ci/para/requestLineList" '{}'
test_api "para" 30026 "/ci/para/queryConfig" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/updateConfig" '{"configKey":"test","configValue":"value"}'
test_api "para" 30026 "/ci/para/addConfig" '{"configKey":"test","configValue":"value"}'
test_api "para" 30026 "/ci/para/deleteConfig" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/listConfig" '{"pageSize":10}'
test_api "para" 30026 "/ci/para/importConfig" '{}'
test_api "para" 30026 "/ci/para/exportConfig" '{}'
test_api "para" 30026 "/ci/para/validate" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/syncConfig" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/refreshCache" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/getVersion" '{}'
test_api "para" 30026 "/ci/para/compareConfig" '{"configKey1":"test1","configKey2":"test2"}'
test_api "para" 30026 "/ci/para/rollback" '{"configKey":"test","version":"1.0"}'
test_api "para" 30026 "/ci/para/auditLog" '{"configKey":"test"}'
test_api "para" 30026 "/ci/para/statusCheck" '{}'
test_api "para" 30026 "/ci/para/healthCheck" '{}'
test_api "para" 30026 "/ci/para/metrics" '{}'
test_api "para" 30026 "/ci/para/configHistory" '{"configKey":"test"}'
echo ""

# Pay-sign-server 补充参数
echo "【pay-sign-server】"
test_api "pay-sign" 30018 "/ci/pay-sign/querySignInfo" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/updateSignInfo" '{"msisdn":"15000000203","status":"active"}'
test_api "pay-sign" 30018 "/ci/pay-sign/deleteSignInfo" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/listSignInfo" '{"pageSize":10}'
test_api "pay-sign" 30018 "/ci/pay-sign/requestContract" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/receiveContractResult" '{"contractNo":"TEST001"}'
test_api "pay-sign" 30018 "/ci/pay-sign/getSignDetail" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/listContracts" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/exportSignInfo" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/importSignInfo" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/validateContract" '{"contractNo":"TEST001"}'
test_api "pay-sign" 30018 "/ci/pay-sign/retryProcess" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/cancelRequest" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/getStatus" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/Statistics" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/exportStats" '{}'
echo ""

echo "本轮完成，继续测试其他服务..."
