#!/bin/bash
# 全面测试所有服务

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

echo "=== 全面测试所有服务 ==="
echo ""

# Fep-dev
test_api "fep-dev" 30009 "/ci/dev/test"
test_api "fep-dev" 30009 "/ci/dev/health"

# Collect-ticket
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestOrder"
test_api "collect-ticket" 30023 "/ci/collect-ticket/requestPayResult"

# Collect-pay
test_api "collect-pay" 30024 "/ci/collect-pay/requestOrder"
test_api "collect-pay" 30024 "/ci/collect-pay/requestPayResult"

# Acc-es
test_api "acc-es" 30028 "/ci/acc-es/query"
test_api "acc-es" 30028 "/ci/acc-es/update"

# Alipay-account
test_api "alipay-account" 30029 "/ci/alipay-account/requestApplication"
test_api "alipay-account" 30029 "/ci/alipay-account/requestKeyList"

# Fep-acc
test_api "fep-acc" 30031 "/ci/fep-acc/requestApplication"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
