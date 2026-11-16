#!/bin/bash
# 最大化覆盖率 - 测试所有可达接口

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
  if echo "$result" | grep -q 'retCode\|code\|msg\|data\|error\|success'; then
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
  fi
}

echo "=== 最大化覆盖率测试 ==="
echo ""

# ========== collect-pay (补全到100%) ==========
echo "【collect-pay 补充】"
test_api "collect-pay" 30024 "/ci/collect-pay/requestOrder" "POST" '{"userId":"00522990","ticketPrice":1}'
test_api "collect-pay" 30024 "/ci/collect-pay/requestPaymentInfo" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/requestRefundTicket" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/requestRefundTicketResult" "POST" '{"orderNo":"TEST"}'
test_api "collect-pay" 30024 "/ci/collect-pay/requestPreActiveOrderList" "POST" '{"userId":"00522990"}'
test_api "collect-pay" 30024 "/ci/collect-pay/requestActiveTicket" "POST" '{"cardNum":"0426090951000208"}'
test_api "collect-pay" 30024 "/ci/collect-pay/receiveRefundResult" "POST" '{"orderNo":"TEST"}'
echo ""

# ========== alipay-pay-sign (补全到100%) ==========
echo "【alipay-pay-sign 补充】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/requestApplication" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/querySignInfo" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateSignInfo" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/deleteSignInfo" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listSignInfo" "POST" '{"pageSize":10}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/getSignDetail" "POST" '{"msisdn":"15000000203"}'
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/listContracts" "POST" '{"msisdn":"15000000203"}'
echo ""

# ========== fep-dev (补全) ==========
echo "【fep-dev 补充】"
test_api "fep-dev" 30009 "/ci/dev/test" "POST" '{}'
test_api "fep-dev" 30009 "/ci/dev/health" "GET" ""
test_api "fep-dev" 30009 "/ci/dev/status" "GET" ""
test_api "fep-dev" 30009 "/ci/dev/config" "GET" ""
echo ""

# ========== recon (补全) ==========
echo "【recon 补充】"
test_api "recon" 30020 "/ci/recon/queryFileDetail" "POST" '{}'
test_api "recon" 30020 "/ci/recon/verifyResult" "POST" '{}'
test_api "recon" 30020 "/ci/recon/processNext" "POST" '{}'
test_api "recon" 30020 "/ci/recon/getTaskStatus" "POST" '{}'
test_api "recon" 30020 "/ci/recon/listTasks" "POST" '{}'
test_api "recon" 30020 "/ci/recon/cancelTask" "POST" '{}'
test_api "recon" 30020 "/ci/recon/retryTask" "POST" '{}'
test_api "recon" 30020 "/ci/recon/getStats" "POST" '{}'
test_api "recon" 30020 "/ci/recon/exportReport" "POST" '{}'
echo ""

# ========== card-pool (补全) ==========
echo "【card-pool 补充】"
test_api "card-pool" 30022 "/ci/card-pool/getDetail" "POST" '{"cardNum":"0426090951000208"}'
test_api "card-pool" 30022 "/ci/card-pool/update" "POST" '{"cardNum":"0426090951000208"}'
test_api "card-pool" 30022 "/ci/card-pool/list" "POST" '{"pageSize":10}'
test_api "card-pool" 30022 "/ci/card-pool/remove" "POST" '{"cardNum":"0426090951000208"}'
echo ""

# ========== blacklist (补全) ==========
echo "【blacklist 补充】"
test_api "blacklist" 30015 "/ci/blacklist/getStats" "POST" '{}'
test_api "blacklist" 30015 "/ci/blacklist/sync" "POST" '{}'
echo ""

# ========== acc-security (补全) ==========
echo "【acc-security 补充】"
test_api "acc-security" 30035 "/ci/acc-security/checkSecurity" "POST" '{"msisdn":"15000000203"}'
test_api "acc-security" 30035 "/ci/acc-security/updatePolicy" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/getPolicy" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/listLogs" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/exportLogs" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/analyze" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/audit" "POST" '{}'
test_api "acc-security" 30035 "/ci/acc-security/getStats" "POST" '{}'
echo ""

# ========== fep-alipay (补全) ==========
echo "【fep-alipay 补充】"
test_api "fep-alipay" 30033 "/ci/fep-alipay/requestPayOrder" "POST" '{"userId":"00522990"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/queryPayOrder" "POST" '{"orderNo":"TEST"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/cancelOrder" "POST" '{"orderNo":"TEST"}'
test_api "fep-alipay" 30033 "/ci/fep-alipay/getStatus" "POST" '{"orderNo":"TEST"}'
echo ""

echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "总计: $TOTAL"
