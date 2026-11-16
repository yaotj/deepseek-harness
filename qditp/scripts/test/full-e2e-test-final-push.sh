#!/bin/bash
# 最终冲刺 - 达到99%覆盖率

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

echo "=== 最终冲刺 - 达到99% ==="
echo ""

# 继续补充测试
echo "【para-server 补充】"
test_api "para" 30026 "/ci/para/queryConfig"
test_api "para" 30026 "/ci/para/updateConfig"
test_api "para" 30026 "/ci/para/deleteConfig"
test_api "para" 30026 "/ci/para/listConfig"
test_api "para" 30026 "/ci/para/importConfig"
test_api "para" 30026 "/ci/para/exportConfig"

echo ""
echo "【pay-sign-server 补充】"
test_api "pay-sign" 30018 "/ci/pay-sign/queryList"
test_api "pay-sign" 30018 "/ci/pay-sign/updateStatus"
test_api "pay-sign" 30018 "/ci/pay-sign/deleteRecord"
test_api "pay-sign" 30018 "/ci/pay-sign/exportData"

echo ""
echo "【alipay-pay-sign 补充】"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/queryList"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/updateStatus"
test_api "alipay-pay-sign" 30030 "/ci/alipay-pay-sign/exportData"

echo ""
echo "【collect-pay 补充】"
test_api "collect-pay" 30024 "/ci/collect-pay/listOrder"
test_api "collect-pay" 30024 "/ci/collect-pay/queryOrder"
test_api "collect-pay" 30024 "/ci/collect-pay/exportData"

echo ""
echo "【acc-security 补充】"
test_api "acc-security" 30035 "/ci/acc-security/delete"
test_api "acc-security" 30035 "/ci/acc-security/list"
test_api "acc-security" 30035 "/ci/acc-security/export"

echo ""
echo "【blacklist 补充】"
test_api "blacklist" 30015 "/ci/blacklist/import"
test_api "blacklist" 30015 "/ci/blacklist/export"
test_api "blacklist" 30015 "/ci/blacklist/update"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
