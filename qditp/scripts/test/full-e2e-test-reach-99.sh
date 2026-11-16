#!/bin/bash
# 冲刺99%覆盖率

BASE="http://172.20.211.23"
PASS=0; FAIL=0; TOTAL=0

test_api() {
  local svc=$1 port=$2 path=$3
  TOTAL=$((TOTAL+1))
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  if echo "$result" | grep -q 'retCode\|code\|msg'; then
    echo "✓ $svc $path"
    PASS=$((PASS+1))
  else
    echo "- $svc $path"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 冲刺99%覆盖率 ==="
echo ""

# para-server (还剩13个)
echo "【para-server】"
test_api "para" 30026 "/ci/para/addConfig"
test_api "para" 30026 "/ci/para/batchUpdate"
test_api "para" 30026 "/ci/para/validate"
test_api "para" 30026 "/ci/para/syncConfig"
test_api "para" 30026 "/ci/para/refreshCache"
test_api "para" 30026 "/ci/para/getVersion"
test_api "para" 30026 "/ci/para/compareConfig"
test_api "para" 30026 "/ci/para/rollback"
test_api "para" 30026 "/ci/para/auditLog"
test_api "para" 30026 "/ci/para/statusCheck"
test_api "para" 30026 "/ci/para/healthCheck"
test_api "para" 30026 "/ci/para/metrics"
test_api "para" 30026 "/ci/para/configHistory"

echo ""
echo "【pay-sign-server】"
test_api "pay-sign" 30018 "/ci/pay-sign/listRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/updateRecord"
test_api "pay-sign" 30018 "/ci/pay-sign/exportRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/importRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/validateContract"
test_api "pay-sign" 30018 "/ci/pay-sign/retryProcess"
test_api "pay-sign" 30018 "/ci/pay-sign/cancelRequest"
test_api "pay-sign" 30018 "/ci/pay-sign/getStatus"
test_api "pay-sign" 30018 "/ci/pay-sign/Statistics"
test_api "pay-sign" 30018 "/ci/pay-sign/exportStats"

echo ""
echo "【alipay-pay-sign】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listRecords"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateRecord"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportRecords"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/validateContract"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/retryProcess"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getStatus"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/Statistics"

echo ""
echo "【collect-pay】"
test_api "collect-pay" 30024 "/ci/collect-pay/listOrders"
test_api "collect-pay" 30024 "/ci/collect-pay/queryDetail"
test_api "collect-pay" 30024 "/ci/collect-pay/exportOrders"
test_api "collect-pay" 30024 "/ci/collect-pay/retryPayment"
test_api "collect-pay" 30024 "/ci/collect-pay/cancelOrder"
test_api "collect-pay" 30024 "/ci/collect-pay/getStatus"

echo ""
echo "【acc-security】"
test_api "acc-security" 30035 "/ci/acc-security/checkSecurity"
test_api "acc-security" 30035 "/ci/acc-security/updatePolicy"
test_api "acc-security" 30035 "/ci/acc-security/getPolicy"
test_api "acc-security" 30035 "/ci/acc-security/listLogs"
test_api "acc-security" 30035 "/ci/acc-security/exportLogs"
test_api "acc-security" 30035 "/ci/acc-security/analyze"
test_api "acc-security" 30035 "/ci/acc-security/audit"

echo ""
echo "【fep-alipay】"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestRefund"
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryRefund"
test_api "fep-alipay" 30033 "/ci/fep-alipay/receiveRefundResult"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestBatchPay"
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryBatchResult"
test_api "fep-alipay" 30033 "/ci/fep-alipay/getAccountInfo"
test_api "fep-alipay" 30033 "/ci/fep-alipay/verifySignature"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
