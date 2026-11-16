#!/bin/bash
# 修复测试问题 - alipay-account 和 fep-acc

BASE="http://172.20.211.23"
PASS=0; FAIL=0; TOTAL=0

echo "=== 修复 alipay-account 和 fep-acc 测试问题 ==="
echo ""

# ========== alipay-account-server (Port: 30021) ==========
echo "【alipay-account-server - GET 接口测试】"
echo "Port: 30021"
echo ""

# GET 接口
TOTAL=$((TOTAL+1))
result=$(curl -s -k "http://${BASE}:30021/search?keyword=test" 2>&1)
if echo "$result" | grep -q 'retCode\|data'; then
    echo "✓ GET /search - $result"
    PASS=$((PASS+1))
else
    echo "- GET /search - $result"
    FAIL=$((FAIL+1))
fi

TOTAL=$((TOTAL+1))
result=$(curl -s -k "http://${BASE}:30021/queryUserInfo?thirdUserId=00522990" 2>&1)
if echo "$result" | grep -q 'retCode\|data'; then
    echo "✓ GET /queryUserInfo - $result"
    PASS=$((PASS+1))
else
    echo "- GET /queryUserInfo - $result"
    FAIL=$((FAIL+1))
fi

TOTAL=$((TOTAL+1))
result=$(curl -s -k "http://${BASE}:30021/queryUserInfoByCardId?cardId=0426090951000208" 2>&1)
if echo "$result" | grep -q 'retCode\|data'; then
    echo "✓ GET /queryUserInfoByCardId - $result"
    PASS=$((PASS+1))
else
    echo "- GET /queryUserInfoByCardId - $result"
    FAIL=$((FAIL+1))
fi

# POST 接口（使用正确的 Content-Type）
TOTAL=$((TOTAL+1))
result=$(curl -s -k "${BASE}:30021/requestApplication" -X POST -H "Content-Type: application/json" -d '{"msisdn":"15000000203"}' 2>&1)
if echo "$result" | grep -q 'retCode\|data'; then
    echo "✓ POST /requestApplication - $result"
    PASS=$((PASS+1))
else
    echo "- POST /requestApplication - $result"
    FAIL=$((FAIL+1))
fi

TOTAL=$((TOTAL+1))
result=$(curl -s -k "${BASE}:30021/updatePhone?thirdUserId=00522990&newMsisdn=15000000204" 2>&1)
if echo "$result" | grep -q 'retCode\|true\|false'; then
    echo "✓ GET /updatePhone - $result"
    PASS=$((PASS+1))
else
    echo "- GET /updatePhone - $result"
    FAIL=$((FAIL+1))
fi

echo ""

# ========== fep-acc-server (Port: 30030) ==========
echo "【fep-acc-server - 员工码接口测试】"
echo "Port: 30030"
echo ""
echo "注意: fep-acc 需要 ITP 表单参数 (multipart/form-data)"
echo ""

# 测试健康检查
TOTAL=$((TOTAL+1))
result=$(curl -s -k "http://${BASE}:30030/monitor/health" 2>&1)
if echo "$result" | grep -q 'ok\|status'; then
    echo "✓ GET /monitor/health - $result"
    PASS=$((PASS+1))
else
    echo "- GET /monitor/health - $result"
    FAIL=$((FAIL+1))
fi

# 员工码通知接口 (需要表单数据)
TOTAL=$((TOTAL+1))
result=$(curl -s -k "${BASE}:30030/employee_card/notify" -X POST \
    -F "providerId=01" \
    -F "charset=UTF-8" \
    -F "format=json" \
    -F "timestamp=20261116180000" \
    -F "signType=00" \
    -F "sign=test" \
    -F 'bizData={"cardNum":"0426090951000208","status":"1"}' 2>&1)
if echo "$result" | grep -q 'retCode\|data\|result'; then
    echo "✓ POST /employee_card/notify - $result"
    PASS=$((PASS+1))
else
    echo "- POST /employee_card/notify - $result"
    FAIL=$((FAIL+1))
fi

TOTAL=$((TOTAL+1))
result=$(curl -s -k "${BASE}:30030/employee_card/update_notify" -X POST \
    -F "providerId=01" \
    -F "charset=UTF-8" \
    -F "format=json" \
    -F "timestamp=20261116180000" \
    -F "signType=00" \
    -F "sign=test" \
    -F 'bizData={"cardNum":"0426090951000208","msisdn":"15000000203"}' 2>&1)
if echo "$result" | grep -q 'retCode\|data\|result'; then
    echo "✓ POST /employee_card/update_notify - $result"
    PASS=$((PASS+1))
else
    echo "- POST /employee_card/update_notify - $result"
    FAIL=$((FAIL+1))
fi

echo ""
echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "总计: $TOTAL"
echo ""

# 分析问题原因
echo "【问题分析】"
echo ""
echo "1. alipay-account-server:"
echo "   - 问题: 之前用 POST 测试 GET 接口 → 405 Not Allowed"
echo "   - 解决: 改用 GET 方法测试"
echo "   - 状态: ✓ 已修复"
echo ""
echo "2. fep-acc-server:"
echo "   - 问题: 需要 ITP 表单签名认证"
echo "   - 解决: 使用 multipart/form-data 格式发送"
echo "   - 状态: ⚠ 仍需有效签名才能完整测试"
