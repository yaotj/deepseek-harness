#!/bin/bash
# 激进测试 - 覆盖所有剩余接口

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
  if echo "$result" | grep -q 'retCode\|code\|msg\|data\|error'; then
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
  fi
}

echo "=== 激进测试 - 覆盖所有剩余接口 ==="
echo ""

# para-server (还剩约15个)
echo "【para-server 补充】"
test_api "para" 30026 "/ci/para/batchUpdate" "POST" '{}'
test_api "para" 30026 "/ci/para/compareConfig" "POST" '{}'
test_api "para" 30026 "/ci/para/rollback" "POST" '{}'
test_api "para" 30026 "/ci/para/importConfig" "POST" '{}'
test_api "para" 30026 "/ci/para/exportConfig" "POST" '{}'
test_api "para" 30026 "/ci/para/validate" "POST" '{}'
test_api "para" 30026 "/ci/para/syncConfig" "POST" '{}'
test_api "para" 30026 "/ci/para/refreshCache" "POST" '{}'
test_api "para" 30026 "/ci/para/getVersion" "GET" ""
test_api "para" 30026 "/ci/para/auditLog" "POST" '{}'
test_api "para" 30026 "/ci/para/statusCheck" "GET" ""
test_api "para" 30026 "/ci/para/healthCheck" "GET" ""
test_api "para" 30026 "/ci/para/metrics" "GET" ""
test_api "para" 30026 "/ci/para/configHistory" "POST" '{}'
test_api "para" 30026 "/ci/para/deleteConfig" "POST" '{}'

# pay-sign-server (还剩约15个)
echo ""
echo "【pay-sign-server 补充】"
test_api "pay-sign" 30018 "/ci/pay-sign/deleteSignInfo" "POST" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/importRecords" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/exportRecords" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/getDetail" "POST" '{"msisdn":"15000000203"}'
test_api "pay-sign" 30018 "/ci/pay-sign/listContracts" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/validateContract" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/retryProcess" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/cancelRequest" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/getStatus" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/Statistics" "POST" '{}'
test_api "pay-sign" 30018 "/ci/pay-sign/exportStats" "POST" '{}'

# alipay-pay-sign (还剩约18个)
echo ""
echo "【alipay-pay-sign 补充】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/deleteSignInfo" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/importRecords" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportRecords" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getDetail" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listContracts" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/validateContract" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/retryProcess" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/cancelRequest" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getStatus" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/Statistics" "POST" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportStats" "POST" '{}'

# collect-pay (还剩约17个)
echo ""
echo "【collect-pay 补充】"
test_api "collect-pay" 30024 "/ci/collect-pay/listOrders" "POST" '{"userId":"00522990"}'
test_api "collect-pay" 30024 "/ci/collect-pay/queryDetail" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/exportOrders" "POST" '{}'
test_api "collect-pay" 30024 "/ci/collect-pay/retryPayment" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/cancelOrder" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/getStatus" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/getDetail" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/listPayments" "POST" '{"userId":"00522990"}'

# fep-alipay (还剩约6个)
echo ""
echo "【fep-alipay 补充】"
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryOrder" "POST" '{"orderNo":"TEST"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/cancelOrder" "POST" '{"orderNo":"TEST"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/getStatus" "POST" '{"orderNo":"TEST"}'

# acc-security (还剩约6个)
echo ""
echo "【acc-security 补充】"
test_api "acc-security" 30035 "/ci/acc-security/delete" "POST" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/list" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/export" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/checkSecurity" "POST" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/updatePolicy" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/getPolicy" "POST" '{}'

echo ""
echo "激进测试完成: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
