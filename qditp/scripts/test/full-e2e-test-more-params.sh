#!/bin/bash
# 更多参数补全测试

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
    echo "- $svc $path"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 更多参数补全测试 ==="
echo ""

# Alipay-pay-sign 补充参数
echo "【alipay-pay-sign-server】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/querySignInfo" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateSignInfo" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/deleteSignInfo" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listSignInfo" '{"pageSize":10}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getSignDetail" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listContracts" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportRecords" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/importRecords" '{}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/validateContract" '{"contractNo":"TEST001"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/retryProcess" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/cancelRequest" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getStatus" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getDetail" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/Statistics" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportStats" '{}'
echo ""

# Fep-alipay 补充参数
echo "【fep-alipay-server】"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestRefund" '{"orderNo":"TEST_ORDER_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryRefund" '{"orderNo":"TEST_ORDER_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/receiveRefundResult" '{"orderNo":"TEST_ORDER_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestBatchPay" '{"userId":"00522990"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryBatchResult" '{"batchNo":"TEST_BATCH_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/getAccountInfo" '{"userId":"00522990"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/verifySignature" '{"data":"test"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestBalance" '{"userId":"00522990"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/getTransactionList" '{"userId":"00522990"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryOrder" '{"orderNo":"TEST_ORDER_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/cancelOrder" '{"orderNo":"TEST_ORDER_001"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/getStatus" '{"orderNo":"TEST_ORDER_001"}'
echo ""

# Acc-secure 补充参数
echo "【acc-secure-server】"
test_api "acc-secure" 30034 "/ci/acc-secure/request" '{"cardNum":"0426090951000208"}'
test_api "acc-secure" 30034 "/ci/acc-secure/query" '{"cardNum":"0426090951000208"}'
test_api "acc-secure" 30034 "/ci/acc-secure/update" '{"cardNum":"0426090951000208"}'
test_api "acc-secure" 30034 "/ci/acc-secure/delete" '{"cardNum":"0426090951000208"}'
test_api "acc-secure" 30034 "/ci/acc-secure/list" '{"pageSize":10}'
test_api "acc-secure" 30034 "/ci/acc-secure/validate" '{"cardNum":"0426090951000208"}'
test_api "acc-secure" 30034 "/ci/acc-secure/encrypt" '{"data":"test"}'
test_api "acc-secure" 30034 "/ci/acc-secure/decrypt" '{"data":"test"}'
echo ""

# Acc-security 补充参数
echo "【acc-security-server】"
test_api "acc-security" 30035 "/ci/acc-security/request" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/query" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/update" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/delete" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/list" '{"pageSize":10}'
test_api "acc-security" 30035 "/ci/acc-security/checkSecurity" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/updatePolicy" '{"policyId":"test"}'
test_api "acc-security" 30035 "/ci/acc-security/getPolicy" '{"policyId":"test"}'
test_api "acc-security" 30035 "/ci/acc-security/listLogs" '{"pageSize":10}'
test_api "acc-security" 30035 "/ci/acc-security/exportLogs" '{}'
test_api "acc-security" 30035 "/ci/acc-security/analyze" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/audit" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/getStats" '{}'
echo ""

# Blacklist 补充参数
echo "【blacklist-server】"
test_api "blacklist" 30015 "/ci/blacklist/query" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/add" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/remove" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/check" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/list" '{"pageSize":10}'
test_api "blacklist" 30015 "/ci/blacklist/export" '{}'
test_api "blacklist" 30015 "/ci/blacklist/import" '{}'
test_api "blacklist" 30015 "/ci/blacklist/update" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/validate" '{"cardNum":"0426090951000208"}'
test_api "blacklist" 30015 "/ci/blacklist/sync" '{}'
test_api "blacklist" 30015 "/ci/blacklist/getStats" '{}'
echo ""

echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
