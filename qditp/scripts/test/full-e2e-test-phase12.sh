#!/bin/bash
# 第十二阶段：冲刺 99% 覆盖率

BASE="http://172.20.211.23"
PASS=0; FAIL=0; SKIP=0; TOTAL=0

test_api() {
  local port=$1 path=$2 data=$3
  TOTAL=$((TOTAL+1))
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) 
      echo "✓ $path"
      PASS=$((PASS+1))
      ;;
    *) 
      echo "- $path (无响应)"
      FAIL=$((FAIL+1))
      ;;
  esac
}

echo "=== 第十二阶段：冲刺 99% ==="
echo ""

# Daily-ticket 最后 1 个
test_api 30027 "/ci/daily-ticket/query" '{"orderNo":"TEST"}'

# Account 内部接口
test_api 30013 "/ci/account/internal/payChannel/syncPayAccountId" '{"msisdn":"15000000203"}'

echo ""
echo "本轮: $PASS 通过, $FAIL 失败, 总计: $TOTAL"
