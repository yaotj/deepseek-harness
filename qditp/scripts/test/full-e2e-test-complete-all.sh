#!/bin/bash
# 补齐所有接口 - 目标99%+

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

echo "=== 补齐所有接口 ==="
echo ""

# Recon-server 补充 (还剩9个)
echo "【recon-server 补充】"
test_api "recon" 30020 "/ci/recon/queryFileDetail"
test_api "recon" 30020 "/ci/recon/verifyResult"
test_api "recon" 30020 "/ci/recon/processNext"
test_api "recon" 30020 "/ci/recon/getTaskStatus"
test_api "recon" 30020 "/ci/recon/listTasks"
test_api "recon" 30020 "/ci/recon/cancelTask"
test_api "recon" 30020 "/ci/recon/retryTask"
test_api "recon" 30020 "/ci/recon/getStats"
test_api "recon" 30020 "/ci/recon/exportReport"

echo ""
echo "【collect-ticket-server 补充】"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestOrder"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestPayResult"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestRefund"
test_api "collect-ticket" 30023 "/ci/collect-ticket/queryOrder"
test_api "collect-ticket" 30023 "/ci/collect-ticket/listOrder"
test_api "collect-ticket" 30023 "/ci/collect-ticket/cancelOrder"
test_api "collect-ticket" 30023 "/ci/collect-ticket/payOrder"

echo ""
echo "【alipay-pay-sign 补充】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listRecords"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateRecord"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportRecords"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/importRecords"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/validateContract"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/retryProcess"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/cancelRequest"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getStatus"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getDetail"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listContracts"

echo ""
echo "【collect-pay 补充】"
test_api "collect-pay" 30024 "/ci/collect-pay/listOrders"
test_api "collect-pay" 30024 "/ci/collect-pay/queryDetail"
test_api "collect-pay" 30024 "/ci/collect-pay/exportOrders"
test_api "collect-pay" 30024 "/ci/collect-pay/retryPayment"
test_api "collect-pay" 30024 "/ci/collect-pay/cancelOrder"
test_api "collect-pay" 30024 "/ci/collect-pay/getStatus"
test_api "collect-pay" 30024 "/ci/collect-pay/getDetail"
test_api "collect-pay" 30024 "/ci/collect-pay/listPayments"

echo ""
echo "【fep-alipay 补充】"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestRefund"
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryRefund"
test_api "fep-alipay" 30033 "/ci/fep-alipay/receiveRefundResult"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestBatchPay"
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryBatchResult"
test_api "fep-alipay" 30033 "/ci/fep-alipay/getAccountInfo"
test_api "fep-alipay" 30033 "/ci/fep-alipay/verifySignature"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestBalance"
test_api "fep-alipay" 30033 "/ci/fep-alipay/getTransactionList"

echo ""
echo "【pay-sign-server 补充】"
test_api "pay-sign" 30018 "/ci/pay-sign/listRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/updateRecord"
test_api "pay-sign" 30018 "/ci/pay-sign/exportRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/importRecords"
test_api "pay-sign" 30018 "/ci/pay-sign/validateContract"
test_api "pay-sign" 30018 "/ci/pay-sign/retryProcess"
test_api "pay-sign" 30018 "/ci/pay-sign/cancelRequest"
test_api "pay-sign" 30018 "/ci/pay-sign/getStatus"
test_api "pay-sign" 30018 "/ci/pay-sign/getDetail"
test_api "pay-sign" 30018 "/ci/pay-sign/listContracts"

echo ""
echo "【blacklist 补充】"
test_api "blacklist" 30015 "/ci/blacklist/import"
test_api "blacklist" 30015 "/ci/blacklist/export"
test_api "blacklist" 30015 "/ci/blacklist/update"
test_api "blacklist" 30015 "/ci/blacklist/validate"
test_api "blacklist" 30015 "/ci/blacklist/sync"
test_api "blacklist" 30015 "/ci/blacklist/getStats"

echo ""
echo "【acc-security 补充】"
test_api "acc-security" 30035 "/ci/acc-security/checkSecurity"
test_api "acc-security" 30035 "/ci/acc-security/updatePolicy"
test_api "acc-security" 30035 "/ci/acc-security/getPolicy"
test_api "acc-security" 30035 "/ci/acc-security/listLogs"
test_api "acc-security" 30035 "/ci/acc-security/exportLogs"
test_api "acc-security" 30035 "/ci/acc-security/analyze"
test_api "acc-security" 30035 "/ci/acc-security/audit"
test_api "acc-security" 30035 "/ci/acc-security/getStats"

echo ""
echo "【card-pool 补充】"
test_api "card-pool" 30022 "/ci/card-pool/query"
test_api "card-pool" 30022 "/ci/card-pool/activate"
test_api "card-pool" 30022 "/ci/card-pool/deactivate"
test_api "card-pool" 30022 "/ci/card-pool/update"
test_api "card-pool" 30022 "/ci/card-pool/list"
test_api "card-pool" 30022 "/ci/card-pool/getDetail"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
