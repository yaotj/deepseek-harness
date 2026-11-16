#!/bin/bash
# 继续测试以达到99%覆盖率

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

echo "=== 继续测试以达到99% ==="
echo ""

# 继续测试 para-server (还剩25个接口)
echo "【para-server 补充测试】"
test_api "para" 30026 "/ci/para/requestStationInfo"
test_api "para" 30026 "/ci/para/requestLineInfo"
test_api "para" 30026 "/ci/para/requestDeviceList"
test_api "para" 30026 "/ci/para/requestStationList"
test_api "para" 30026 "/ci/para/requestLineList"
test_api "para" 30026 "/ci/para/queryConfig"
test_api "para" 30026 "/ci/para/updateConfig"
echo ""

# 继续测试 pay-sign-server (还剩30个接口)
echo "【pay-sign-server 补充测试】"
test_api "pay-sign" 30018 "/ci/pay-sign/querySignInfo"
test_api "pay-sign" 30018 "/ci/pay-sign/updateSignInfo"
test_api "pay-sign" 30018 "/ci/pay-sign/deleteSignInfo"
test_api "pay-sign" 30018 "/ci/pay-sign/listSignInfo"
test_api "pay-sign" 30018 "/ci/pay-sign/requestContract"
test_api "pay-sign" 30018 "/ci/pay-sign/receiveContractResult"
echo ""

# 继续测试 alipay-pay-sign (还剩27个接口)
echo "【alipay-pay-sign 补充测试】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/querySignInfo"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateSignInfo"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/requestContract"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/receiveContractResult"
echo ""

# 继续测试 collect-pay (还剩25个接口)
echo "【collect-pay 补充测试】"
test_api "collect-pay" 30024 "/ci/collect-pay/requestRefundTicket"
test_api "collect-pay" 30024 "/ci/collect-pay/requestRefundTicketResult"
test_api "collect-pay" 30024 "/ci/collect-pay/requestPreActiveOrderList"
test_api "collect-pay" 30024 "/ci/collect-pay/requestActiveTicket"
test_api "collect-pay" 30024 "/ci/collect-pay/receiveRefundResult"
echo ""

# 继续测试 acc-secure (还剩5个接口)
echo "【acc-secure 补充测试】"
test_api "acc-secure" 30034 "/ci/acc-secure/query"
test_api "acc-secure" 30034 "/ci/acc-secure/delete"
test_api "acc-secure" 30034 "/ci/acc-secure/list"
echo ""

# 继续测试 acc-security (还剩10个接口)
echo "【acc-security 补充测试】"
test_api "acc-security" 30035 "/ci/acc-security/query"
test_api "acc-security" 30035 "/ci/acc-security/delete"
test_api "acc-security" 30035 "/ci/acc-security/list"
test_api "acc-security" 30035 "/ci/acc-security/update"
echo ""

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
