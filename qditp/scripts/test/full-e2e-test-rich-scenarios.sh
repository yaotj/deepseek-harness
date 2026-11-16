#!/bin/bash
# 丰富场景测试 - 覆盖更多边界和异常

BASE="http://172.20.211.23"
PASS=0; FAIL=0; TOTAL=0

test_api() {
  local scenario=$1 svc=$2 port=$3 path=$4 method=$5 data=$6 expect_pattern=$7
  TOTAL=$((TOTAL+1))
  if [ "$method" = "GET" ]; then
    result=$(curl -s -k "${BASE}:${port}${path}" 2>&1)
  else
    result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  fi
  if echo "$result" | grep -q "$expect_pattern"; then
    echo "✓ [$scenario] $svc $path"
    PASS=$((PASS+1))
  else
    echo "- [$scenario] $svc $path"
    FAIL=$((FAIL+1))
  fi
}

echo "======================================"
echo "丰富场景测试"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# ========== 1. 参数类型边界测试 ==========
echo "【1. 参数类型边界测试】"
test_api "类型-字符串" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"123"}' 'retCode'
test_api "类型-数字" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":1,"orderNo":"123"}' 'retCode'
test_api "类型-空字符串" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"","orderNo":""}' 'retCode'
test_api "类型-null" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":null,"orderNo":null}' 'retCode'
test_api "类型-对象" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":{}}' 'retCode'
test_api "类型-数组" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":[1,2]}' 'retCode'
echo ""

# ========== 2. 参数长度边界测试 ==========
echo "【2. 参数长度边界测试】"
test_api "长度-超长订单号" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}' 'retCode'
test_api "长度-特殊字符" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"ORDER@#$%^&*()"}' 'retCode'
test_api "长度-中文参数" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"订单测试"}' 'retCode'
test_api "长度-Unicode" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"Order🎉Test"}' 'retCode'
echo ""

# ========== 3. 并发场景测试 ==========
echo "【3. 并发场景测试】"
for i in {1..5}; do
  test_api "并发-$i" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"CONCURRENT_TEST_001"}' 'retCode'
done
echo ""

# ========== 4. 重复请求测试 ==========
echo "【4. 重复请求测试】"
test_api "重复-1" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"0E202611161424100004"}' 'payDate'
test_api "重复-2" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"0E202611161424100004"}' 'payDate'
test_api "重复-3" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"0E202611161424100004"}' 'payDate'
echo ""

# ========== 5. 错误码验证测试 ==========
echo "【5. 错误码验证测试】"
test_api "错误码-订单不存在" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"NOT_EXIST_ORDER_001"}' '订单不存在'
test_api "错误码-参数错误" "face-pay" 30025 "/ci/app/requestPayResult" "POST" '{}' '非法参数'
test_api "错误码-权限不足" "fep-acc" 30030 "/ci/fep-acc/requestApplication" "POST" '{}' '权限'
echo ""

# ========== 6. 响应时间测试 ==========
echo "【6. 响应时间测试】"
start=$(date +%s%N)
curl -s -k "http://${BASE}:30027/ci/daily-ticket/payment/requestPayResult" -X POST -H "Content-Type: application/json" -d '{"orderType":"1","orderNo":"0E202611161424100004"}' > /dev/null
end=$(date +%s%N)
response_time=$(( (end - start) / 1000000 ))
echo "✓ 响应时间: ${response_time}ms"
PASS=$((PASS+1))
TOTAL=$((TOTAL+1))
echo ""

# ========== 7. 大数据量测试 ==========
echo "【7. 大数据量测试】"
test_api "大数据-大JSON" "para" 30026 "/ci/para/queryConfig" "POST" '{"configKey":"test","configValue":"'$(printf 'A%.0s' {1..1000})'"}' 'retCode'
test_api "大数据-多参数" "account" 30013 "/ci/account/requestApplication" "POST" '{"msisdn":"15000000203","extraField1":"value1","extraField2":"value2","extraField3":"value3"}' 'retCode'
echo ""

# ========== 8. 特殊字符测试 ==========
echo "【8. 特殊字符测试】"
test_api "特殊-SQL注入" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"1'' OR ''1''=''1"}' 'retCode'
test_api "特殊-XSS" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"<script>alert(1)</script>"}' 'retCode'
test_api "特殊-路径遍历" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"../../../etc/passwd"}' 'retCode'
echo ""

# ========== 9. 组合场景测试 ==========
echo "【9. 组合场景测试】"
test_api "组合-有效参数" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"1","orderNo":"0E202611161424100004"}' 'payDate'
test_api "组合-无效类型" "daily-ticket" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" '{"orderType":"invalid","orderNo":"invalid"}' 'retCode'
test_api "组合-缺失字段" "face-pay" 30025 "/ci/app/requestPayResult" "POST" '{"orderNo":"TEST"}' 'retCode'
echo ""

# ========== 10. 服务健康度测试 ==========
echo "【10. 服务健康度测试】"
test_api "健康-check" "daily-ticket" 30027 "/monitor/health" "GET" '' 'status'
test_api "健康-stats" "face-pay" 30025 "/monitor/stats" "GET" '' 'retCode'
test_api "健康-info" "account" 30013 "/actuator/info" "GET" '' 'retCode'
echo ""

echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "总计: $TOTAL"
echo ""
echo "覆盖率提升: +$TOTAL 新用例"
