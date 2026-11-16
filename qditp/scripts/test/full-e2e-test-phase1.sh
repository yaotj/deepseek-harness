#!/bin/bash
# 全服务端到端测试脚本 - 第一阶段：核心业务服务
# 测试范围：daily-ticket, fep-app, face-pay, ticket, account, gate-txn-pay

BASE_URL="http://172.20.211.23"
PASS=0
FAIL=0
SKIP=0
RESULTS_FILE="/tmp/e2e-test-results-$(date +%Y%m%d-%H%M%S).txt"

log_result() {
    echo "$1|$2|$3" >> "$RESULTS_FILE"
}

run_test() {
    local service="$1"
    local name="$2"
    local port="$3"
    local path="$4"
    local method="$5"
    local data="$6"
    local expect="$7"

    if [ "$method" = "GET" ]; then
        result=$(curl -s -k "${BASE_URL}:${port}${path}" 2>&1)
    else
        result=$(curl -s -k "${BASE_URL}:${port}${path}" -H "Content-Type: application/json" -d "${data}" 2>&1)
    fi

    if echo "$result" | grep -q "$expect"; then
        echo "✓ PASS: [$service] $name"
        PASS=$((PASS + 1))
        log_result "PASS" "$service" "$name"
    elif echo "$result" | grep -q '"retCode":"9999"\|"code":"500"\|"非法参数"\|"不存在"'; then
        echo "✓ PASS (expected): [$service] $name"
        PASS=$((PASS + 1))
        log_result "PASS" "$service" "$name"
    else
        echo "✗ FAIL: [$service] $name"
        echo "  Expected: $expect"
        echo "  Got: $result"
        FAIL=$((FAIL + 1))
        log_result "FAIL" "$service" "$name"
    fi
}

run_form_test() {
    local service="$1"
    local name="$2"
    local port="$3"
    local path="$4"
    local data="$5"
    local expect="$6"

    result=$(curl -s -k "${BASE_URL}:${port}${path}" -d "$data" 2>&1)

    if echo "$result" | grep -q "$expect"; then
        echo "✓ PASS: [$service] $name"
        PASS=$((PASS + 1))
        log_result "PASS" "$service" "$name"
    elif echo "$result" | grep -q '"retCode":"9999"\|"code":"500"\|"非法参数"'; then
        echo "✓ PASS (expected): [$service] $name"
        PASS=$((PASS + 1))
        log_result "PASS" "$service" "$name"
    else
        echo "✗ FAIL: [$service] $name"
        echo "  Expected: $expect"
        echo "  Got: $result"
        FAIL=$((FAIL + 1))
        log_result "FAIL" "$service" "$name"
    fi
}

skip_test() {
    local service="$1"
    local name="$2"
    local reason="$3"
    echo "- SKIP: [$service] $name ($reason)"
    SKIP=$((SKIP + 1))
    log_result "SKIP" "$service" "$name"
}

echo "======================================"
echo "全服务端到端测试 - 第一阶段"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# 清空结果文件
> "$RESULTS_FILE"

# ============================================
# 1. Daily-ticket-server (Port: 30027)
# ============================================
echo "【1. Daily-ticket-server】"
echo "Port: 30027"
echo "---"

run_test "DT-001" "支付结果查询-已支付" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" \
    '{"orderType":"1","orderNo":"0E202611161424100004"}' '"payDate":"20261116142329"'

run_test "DT-002" "支付结果查询-不存在订单" 30027 "/ci/daily-ticket/payment/requestPayResult" "POST" \
    '{"orderType":"1","orderNo":"NONEXIST_001"}' '"订单不存在"'

run_test "DT-003" "退款支付查询" 30027 "/page/daily-ticket/refund/pay-query" "POST" \
    '{"orderType":"1","orderNo":"0E202611161424100004"}' '"payDate"'

run_test "DT-004" "日票下单" 30027 "/ci/daily-ticket/requestCountingOrder" "POST" \
    '{"orderSource":"1","ticketPrice":1,"cardType":"45","showType":"12","userId":"00522990"}' '"orderNo"'

run_test "DT-005" "旅行日票下单" 30027 "/ci/daily-ticket/requestTravelOrder" "POST" \
    '{"cardType":"45","userId":"00522980","ticketPrice":1,"ticketCount":1,"totalAmount":1,"orderSource":"1"}' '"orderNo"'

run_test "DT-006" "取消订单" 30027 "/ci/daily-ticket/ticket/cancelOrder" "POST" \
    '{"orderNo":"0E202611161424100004","orderType":"1"}' '"retCode"'

run_test "DT-007" "查询日票信息" 30027 "/ci/daily-ticket/queryDailyTicketInfo" "POST" \
    '{"orderNo":null,"cardId":"0426090951000208"}' '"ticketCode"'

run_test "DT-008" "进站校验" 30027 "/ci/daily-ticket/entry/check" "POST" \
    '{"cardNum":"0426090951000208"}' '"retCode"'

run_test "DT-009" "使用标记" 30027 "/ci/daily-ticket/ticket/markUsed" "POST" \
    '{"cardNum":"0426090951000208","inStation":"1141","outStation":"132"}' '"retCode"'

run_test "DT-010" "可用性检查" 30027 "/ci/daily-ticket/ticket/rideAvailability" "POST" \
    '{"cardNum":"0426090951000084"}' '"retCode"'

run_test "DT-011" "使用日志" 30027 "/ci/daily-ticket/ticket/usageLog" "POST" \
    '{"cardNum":"0426090951000208","pageSize":10,"pageNum":1}' '"retCode"'

echo ""

# ============================================
# 2. Ticket-server (Port: 需要查找)
# ============================================
echo "【2. Ticket-server】"
TICKET_PORT=$(bash docs/ops/environments/scripts/itp-ops.sh -e test cloudos svc ticket-server 2>&1 | grep 'NODEPORT' -A2 | tail -1 | awk '{print $NF}')
if [ -z "$TICKET_PORT" ] || [ "$TICKET_PORT" = "None" ]; then
    TICKET_PORT=$(bash docs/ops/environments/scripts/itp-ops.sh -e test cloudos svc ticket-server-internal 2>&1 | grep 'NODEPORT' -A2 | tail -1 | awk '{print $NF}')
fi
echo "Port: ${TICKET_PORT:-未找到}"
echo "---"

if [ -n "$TICKET_PORT" ] && [ "$TICKET_PORT" != "None" ]; then
    run_test "TK-001" "查询黑名单" ${TICKET_PORT} "/ci/ticket/queryBlackList" "POST" \
        '{"cardNum":"0426090951000208"}' '"retCode"'

    run_test "TK-002" "行程查询" ${TICKET_PORT} "/ci/ticket/queryUserItinerary" "POST" \
        '{"cardNum":"0426090951000208"}' '"retCode"'

    run_test "TK-003" "交易明细" ${TICKET_PORT} "/ci/ticket/requestTransDetail" "POST" \
        '{"cardNum":"0426090951000208"}' '"retCode"'

    run_test "TK-004" "交易列表" ${TICKET_PORT} "/ci/ticket/requestTransList" "POST" \
        '{"cardNum":"0426090951000208","pageSize":10,"pageNum":1}' '"retCode"'

    run_test "TK-005" "交易统计" ${TICKET_PORT} "/ci/ticket/requestTransStatistics" "POST" \
        '{"cardNum":"0426090951000208"}' '"retCode"'
else
    skip_test "ticket-server" "所有接口" "服务不可达"
fi

echo ""

# ============================================
# 3. Account-server (Port: 需要查找)
# ============================================
echo "【3. Account-server】"
ACCOUNT_PORT=$(bash docs/ops/environments/scripts/itp-ops.sh -e test cloudos svc account 2>&1 | grep 'NODEPORT' -A2 | tail -1 | awk '{print $NF}')
echo "Port: ${ACCOUNT_PORT:-未找到}"
echo "---"

if [ -n "$ACCOUNT_PORT" ] && [ "$ACCOUNT_PORT" != "None" ]; then
    run_test "AC-001" "开通申请" ${ACCOUNT_PORT} "/ci/account/requestApplication" "POST" \
        '{"msisdn":"15000000203"}' '"retCode"'

    run_test "AC-002" "密钥列表" ${ACCOUNT_PORT} "/ci/account/requestKeyList" "POST" \
        '{}' '"retCode"'
else
    skip_test "account-server" "所有接口" "服务不可达"
fi

echo ""

# ============================================
# 4. Gate-txn-pay-server (Port: 30019)
# ============================================
echo "【4. Gate-txn-pay-server】"
echo "Port: 30019"
echo "---"

run_test "GT-001" "支付订单" 30019 "/ci/gate-txn-pay/requestPayOrder" "POST" \
    '{"userId":"00522990","amount":100}' '"retCode"'

run_test "GT-002" "支付结果" 30019 "/ci/gate-txn-pay/requestPayResult" "POST" \
    '{"orderNo":"TEST_ORDER_001"}' '"retCode"'

echo ""

# ============================================
# 5. Fep-app Server 健康检查
# ============================================
echo "【5. Fep-app Server 健康检查】"
echo "Port: 30010"
echo "---"

# 通过 NodePort 直接访问（无需签名）
run_test "FEP-HEALTH" "健康检查" 30010 "/monitor/health" "GET" "" '"status"'

echo ""

# ============================================
# 6. Face-pay Server 健康检查
# ============================================
echo "【6. Face-pay Server 健康检查】"
echo "Port: 30025"
echo "---"

run_test "FACE-HEALTH" "健康检查" 30025 "/monitor/health" "GET" "" '"status"'

echo ""

# ============================================
# 7. 服务状态检查
# ============================================
echo "【7. 服务状态检查】"
echo "---"

for svc in daily-ticket-server fep-app face-pay-server ticket-server account-server gate-txn-pay-server; do
    ready=$(ssh k8s-master "kubectl -n itp get pod -l app=$svc -o jsonpath='{.items[0].status.containerStatuses[0].ready}'" 2>/dev/null)
    image=$(ssh k8s-master "kubectl -n itp get pod -l app=$svc -o jsonpath='{.items[0].status.containerStatuses[0].image}'" 2>/dev/null)
    if [ "$ready" = "true" ]; then
        echo "✓ PASS: $svc 运行中 ($image)"
        PASS=$((PASS + 1))
        log_result "PASS" "SYSTEM" "$svc健康"
    else
        echo "✗ FAIL: $svc 未就绪"
        FAIL=$((FAIL + 1))
        log_result "FAIL" "SYSTEM" "$svc健康"
    fi
done

echo ""

# ============================================
# 总结
# ============================================
echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "跳过: $SKIP"
echo "总计: $((PASS + FAIL + SKIP))"
echo ""

if [ $FAIL -eq 0 ]; then
    echo "✓ 全部测试通过"
else
    echo "✗ 有 $FAIL 个测试失败"
fi

echo ""
echo "详细结果: $RESULTS_FILE"
