#!/bin/bash
# fep-app 端到端测试脚本
# 测试范围：所有通过 daily-ticket-server 的接口（受 payDate 格式修改影响）

BASE_URL="http://172.20.211.23:30027"
PASS=0
FAIL=0
RESULTS=()

run_test() {
    local name="$1"
    local method="$2"
    local path="$3"
    local body="$4"
    local expect_pattern="$5"

    if [ "$method" = "GET" ]; then
        result=$(curl -s -k "${BASE_URL}${path}" 2>&1)
    else
        result=$(curl -s -k "${BASE_URL}${path}" -H "Content-Type: application/json" -d "${body}" 2>&1)
    fi

    # 检查响应
    if echo "$result" | grep -q "$expect_pattern"; then
        echo "✓ PASS: $name"
        echo "  Response: $result"
        PASS=$((PASS + 1))
        RESULTS+=("PASS|$name|$result")
    else
        echo "✗ FAIL: $name"
        echo "  Expected pattern: $expect_pattern"
        echo "  Response: $result"
        FAIL=$((FAIL + 1))
        RESULTS+=("FAIL|$name|$result")
    fi
    echo ""
}

echo "======================================"
echo "fep-app 端到端测试 - daily-ticket 相关接口"
echo "测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# ============================================
# 1. IF8A-62: 日票支付结果查询 (requestPayResult)
# ============================================
echo "【IF8A-62】日票支付结果查询"
echo "---"

# 1.1 已支付订单
run_test "已支付日票订单" \
    "POST" \
    "/ci/daily-ticket/payment/requestPayResult" \
    '{"orderType":"1","orderNo":"0E202611161424100004"}' \
    '"payDate":"20261116142329"'

# 1.2 不存在的订单
run_test "不存在的订单" \
    "POST" \
    "/ci/daily-ticket/payment/requestPayResult" \
    '{"orderType":"1","orderNo":"NONEXIST_ORDER_001"}' \
    '"retCode":"9999"'

# 1.3 非法 orderType
run_test "非法 orderType" \
    "POST" \
    "/ci/daily-ticket/payment/requestPayResult" \
    '{"orderType":"9","orderNo":"0E202611161424100004"}' \
    '"orderType必须为1或2"'

# 1.4 旅行日票（orderType=2）
run_test "旅行日票订单（不存在）" \
    "POST" \
    "/ci/daily-ticket/payment/requestPayResult" \
    '{"orderType":"2","orderNo":"0E202611161424100004"}' \
    '"旅游票主订单不存在"'

echo ""

# ============================================
# 2. IF8A-63: 日票支付查询 (queryPayTicket)
# ============================================
echo "【IF8A-63】日票支付查询（退款模块）"
echo "---"

# 2.1 通过 refund/pay-query 接口
run_test "退款支付查询-已支付订单" \
    "POST" \
    "/page/daily-ticket/refund/pay-query" \
    '{"orderType":"1","orderNo":"0E202611161424100004"}' \
    '"payDate":"20261116142329"'

# 2.2 不存在的订单
run_test "退款支付查询-不存在订单" \
    "POST" \
    "/page/daily-ticket/refund/pay-query" \
    '{"orderType":"1","orderNo":"NONEXIST_ORDER_001"}' \
    '"订单不存在"'

echo ""

# ============================================
# 3. 其他日票相关接口（验证未被破坏）
# ============================================
echo "【其他日票接口】验证未被破坏"
echo "---"

# 3.1 requestOrder（下单）
run_test "日票下单" \
    "POST" \
    "/ci/daily-ticket/requestCountingOrder" \
    '{"orderSource":"1","ticketPrice":1,"cardType":"45","showType":"12","userId":"00522990"}' \
    '"retCode"'

# 3.2 requestTravelOrder（旅行日票下单）
run_test "旅行日票下单" \
    "POST" \
    "/ci/daily-ticket/requestTravelOrder" \
    '{"cardType":"45","userId":"00522980","ticketPrice":1,"ticketCount":1,"totalAmount":1,"orderSource":"1"}' \
    '"retCode"'

# 3.3 cancelOrder（取消订单）
run_test "取消订单" \
    "POST" \
    "/ci/daily-ticket/ticket/cancelOrder" \
    '{"orderNo":"0E202611161424100004","orderType":"1"}' \
    '"retCode"'

# 3.4 queryDailyTicketInfo（查询日票信息）
run_test "查询日票信息" \
    "POST" \
    "/ci/daily-ticket/queryDailyTicketInfo" \
    '{"orderNo":null,"cardId":"0426090951000208"}' \
    '"retCode":"0000"'

# 3.5 check（进站校验）
run_test "进站校验" \
    "POST" \
    "/ci/daily-ticket/entry/check" \
    '{"cardNum":"0426090951000208"}' \
    '"retCode"'

# 3.6 markUsed（使用标记）
run_test "使用标记" \
    "POST" \
    "/ci/daily-ticket/ticket/markUsed" \
    '{"cardNum":"0426090951000208","orderNo":null,"countingEnd":null,"inStation":"1141","outStation":"132"}' \
    '"retCode"'

# 3.7 rideAvailability（可用性检查）
run_test "可用性检查" \
    "POST" \
    "/ci/daily-ticket/ticket/rideAvailability" \
    '{"cardNum":"0426090951000084"}' \
    '"retCode"'

# 3.8 usageLog（使用日志）
run_test "使用日志" \
    "POST" \
    "/ci/daily-ticket/ticket/usageLog" \
    '{"cardNum":"0426090951000208","pageSize":10,"pageNum":1}' \
    '"retCode"'

# 3.9 syncOrder（同步订单）
run_test "同步订单" \
    "POST" \
    "/ci/daily-ticket/syncOrder" \
    '{"orderNo":"0E202611161424100004","orderType":"1","event":"1"}' \
    '"retCode"'

# 3.10 requestOrderFree（免费下单）
run_test "免费下单" \
    "POST" \
    "/ci/daily-ticket/requestOrderFree" \
    '{"orderNo":"0E202611161424100004","orderType":"1"}' \
    '"retCode"'

echo ""

# ============================================
# 4. payDate 格式专项验证
# ============================================
echo "【payDate 格式专项验证】"
echo "---"

# 4.1 验证已支付订单的 payDate 是 14 位数字
echo "测试 4.1: 已支付订单 payDate 格式"
result=$(curl -s -k "${BASE_URL}/ci/daily-ticket/payment/requestPayResult" \
    -H "Content-Type: application/json" \
    -d '{"orderType":"1","orderNo":"0E202611161424100004"}' 2>&1)
pay_date=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('payDate',''))" 2>/dev/null)
if [[ "$pay_date" =~ ^[0-9]{14}$ ]]; then
    echo "✓ PASS: payDate=$pay_date (14位数字格式)"
    PASS=$((PASS + 1))
    RESULTS+=("PASS|payDate格式验证|$pay_date")
else
    echo "✗ FAIL: payDate=$pay_date (期望14位数字)"
    FAIL=$((FAIL + 1))
    RESULTS+=("FAIL|payDate格式验证|$pay_date")
fi
echo ""

# 4.2 验证 null payDate
echo "测试 4.2: 不存在订单 payDate 为 null"
result=$(curl -s -k "${BASE_URL}/ci/daily-ticket/payment/requestPayResult" \
    -H "Content-Type: application/json" \
    -d '{"orderType":"1","orderNo":"NONEXIST_ORDER_001"}' 2>&1)
pay_date=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('payDate'))" 2>/dev/null)
if [ "$pay_date" = "None" ] || [ -z "$pay_date" ]; then
    echo "✓ PASS: payDate=null (正确)"
    PASS=$((PASS + 1))
    RESULTS+=("PASS|payDate-null验证|nil")
else
    echo "✗ FAIL: payDate=$pay_date (期望null)"
    FAIL=$((FAIL + 1))
    RESULTS+=("FAIL|payDate-null验证|$pay_date")
fi
echo ""

# ============================================
# 总结
# ============================================
echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "总计: $((PASS + FAIL))"
echo ""

if [ $FAIL -eq 0 ]; then
    echo "✓ 全部测试通过"
    exit 0
else
    echo "✗ 有 $FAIL 个测试失败"
    exit 1
fi
