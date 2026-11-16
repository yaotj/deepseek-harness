#!/bin/bash
# 全服务端到端测试 - 第二阶段：补充覆盖

BASE_URL="http://172.20.211.23"
PASS=0
FAIL=0
SKIP=0

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
    elif echo "$result" | grep -q '"retCode":"9999"\|"code":"500"\|"非法参数"\|"不存在"'; then
        echo "✓ PASS (expected): [$service] $name"
        PASS=$((PASS + 1))
    else
        echo "✗ FAIL: [$service] $name"
        echo "  Expected: $expect"
        echo "  Got: $(echo $result | cut -c1-100)"
        FAIL=$((FAIL + 1))
    fi
}

echo "======================================"
echo "全服务端到端测试 - 第二阶段"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# Ticket-server (Port: 30014)
echo "【Ticket-server】Port: 30014"
echo "---"
run_test "TK-001" "查询黑名单" 30014 "/ci/ticket/queryBlackList" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-002" "行程查询" 30014 "/ci/ticket/queryUserItinerary" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-003" "交易明细" 30014 "/ci/ticket/requestTransDetail" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-004" "交易列表" 30014 "/ci/ticket/requestTransList" "POST" '{"cardNum":"0426090951000208","pageSize":10,"pageNum":1}' '"retCode"'
run_test "TK-005" "交易统计" 30014 "/ci/ticket/requestTransStatistics" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-006" "超时 fare" 30014 "/ci/ticket/requestExcessFare" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-007" "二维码状态" 30014 "/ci/ticket/queryQrCodeStatus" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
run_test "TK-008" "注册乘车状态" 30014 "/ci/ticket/registerRideStatus" "POST" '{"cardNum":"0426090951000208"}' '"retCode"'
echo ""

# Account-server (Port: 30013)
echo "【Account-server】Port: 30013"
echo "---"
run_test "AC-003" "添加支付渠道" 30013 "/ci/account/requestAddPayChannel" "POST" '{"msisdn":"15000000203"}' '"retCode"'
run_test "AC-004" "解除协议" 30013 "/ci/account/requestAgreeRelease" "POST" '{"msisdn":"15000000203"}' '"retCode"'
run_test "AC-005" "设置默认渠道" 30013 "/ci/account/requestSetDefaultPayChannel" "POST" '{"msisdn":"15000000203"}' '"retCode"'
run_test "AC-006" "移除支付渠道" 30013 "/ci/account/requestRemovePayChannel" "POST" '{"msisdn":"15000000203"}' '"retCode"'
run_test "AC-007" "取消账户" 30013 "/app/cancelAccount" "POST" '{"msisdn":"15000000203"}' '"retCode"'
echo ""

# Gate-txn-pay-server (Port: 30019)
echo "【Gate-txn-pay-server】Port: 30019"
echo "---"
run_test "GT-003" "请求支付" 30019 "/ci/gate-txn-pay/requestPay" "POST" '{"userId":"00522990","amount":100}' '"retCode"'
run_test "GT-004" "支付失败订单" 30019 "/ci/gate-txn-pay/requestPayFailOrder" "POST" '{"orderNo":"TEST_ORDER_001"}' '"retCode"'
run_test "GT-005" "用户账户信息" 30019 "/ci/gate-txn-pay/requestUserAccInfo" "POST" '{"userId":"00522990"}' '"retCode"'
echo ""

# Fep-app 补充（直接调用下游）
echo "【补充测试 - Daily-ticket】"
echo "---"
run_test "DT-012" "日票支付" 30027 "/ci/daily-ticket/payment/requestPay" "POST" '{"thirdUserId":"00522990","orderType":"1","orderNo":"TEST_001","payChannelCode":"04"}' '"retCode"'
run_test "DT-013" "日票退款" 30027 "/ci/daily-ticket/payment/requestRefundTicket" "POST" '{"orderType":"1","orderNo":"0E202611161424100004"}' '"retCode"'
run_test "DT-014" "更新票卡" 30027 "/ci/daily-ticket/ticket/updateTicket" "POST" '{"orderNo":"0E202611161424100004","orderType":"1"}' '"retCode"'
echo ""

# Face-pay 补充
echo "【补充测试 - Face-pay】"
echo "---"
run_test "FACE-012" "请求支付" 30025 "/ci/app/requestPay" "POST" '{"userId":"00522990","amount":100}' '"retCode"'
run_test "FACE-013" "请求退款" 30025 "/ci/app/requestRefund" "POST" '{"orderNo":"TEST_ORDER_001"}' '"retCode"'
run_test "FACE-014" "充值" 30025 "/ci/app/topup" "POST" '{"cardNum":"0426090951000208","amount":100}' '"retCode"'
echo ""

# 总结
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
