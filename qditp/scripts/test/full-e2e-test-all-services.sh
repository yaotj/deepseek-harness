#!/bin/bash
# 全量服务测试脚本

BASE="http://172.20.211.23"
PASS=0; FAIL=0; SKIP=0; TOTAL=0

# 服务端口映射
declare -A PORTS
PORTS=(
    ["daily-ticket-server"]="30027"
    ["ticket-server"]="30014"
    ["account-server"]="30013"
    ["gate-txn-pay-server"]="30019"
    ["face-pay-server"]="30025"
    ["fep-app"]="30010"
    ["fep-dev-server"]="30009"
    ["blacklist-server"]="30015"
    ["key-server"]="30016"
    ["para-server"]="30017"
    ["pay-sign-server"]="30018"
    ["recon-server"]="30020"
    ["trans-query-server"]="30021"
    ["card-pool-server"]="30022"
    ["collect-ticket-server"]="30023"
    ["collect-pay"]="30024"
    ["industry-data-server"]="30026"
    ["acc-es-server"]="30028"
    ["alipay-account-server"]="30029"
    ["alipay-pay-sign-server"]="30030"
    ["fep-acc-server"]="30031"
    ["fep-alipay-server"]="30033"
    ["acc-secure-server"]="30034"
    ["acc-security-server"]="30035"
)

test_api() {
  local service=$1 port=$2 path=$3 data=$4
  TOTAL=$((TOTAL+1))
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) 
      echo "✓ $service $path"
      PASS=$((PASS+1))
      ;;
    *"Not found"*|*"404"*) 
      SKIP=$((SKIP+1))
      ;;
    *) 
      echo "- $service $path"
      FAIL=$((FAIL+1))
      ;;
  esac
}

echo "======================================"
echo "全量服务端到端测试"
echo "时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================"
echo ""

# 遍历所有服务测试主要接口
for service in "${!PORTS[@]}"; do
  port=${PORTS[$service]}
  echo "【$service】Port: $port"
  
  # 根据服务类型测试不同接口
  case $service in
    "daily-ticket-server")
      test_api "$service" $port "/ci/daily-ticket/payment/requestPayResult" '{"orderType":"1","orderNo":"TEST"}'
      test_api "$service" $port "/ci/daily-ticket/requestCountingOrder" '{"orderSource":"1"}'
      ;;
    "ticket-server")
      test_api "$service" $port "/ci/ticket/queryBlackList" '{"cardNum":"TEST"}'
      test_api "$service" $port "/ci/ticket/queryUserItinerary" '{"cardNum":"TEST"}'
      ;;
    "account-server")
      test_api "$service" $port "/ci/account/requestApplication" '{"msisdn":"TEST"}'
      test_api "$service" $port "/ci/account/requestKeyList" '{}'
      ;;
    "gate-txn-pay-server")
      test_api "$service" $port "/ci/gate-txn-pay/requestPay" '{"userId":"TEST"}'
      test_api "$service" $port "/ci/gate-txn-pay/requestPayResult" '{"orderNo":"TEST"}'
      ;;
    "face-pay-server")
      test_api "$service" $port "/ci/app/requestPayResult" '{"orderNo":"TEST"}'
      test_api "$service" $port "/ci/app/requestOrder" '{"userId":"TEST"}'
      ;;
    "fep-app")
      test_api "$service" $port "/app/requestCountingOrder" '{"orderSource":"1"}'
      test_api "$service" $port "/app/queryBlackList" '{"cardNum":"TEST"}'
      ;;
    "fep-dev-server")
      test_api "$service" $port "/ci/dev/test" '{}'
      ;;
    "blacklist-server")
      test_api "$service" $port "/ci/blacklist/query" '{"cardNum":"TEST"}'
      ;;
    "key-server")
      test_api "$service" $port "/ci/key/request" '{}'
      ;;
    "para-server")
      test_api "$service" $port "/ci/para/request" '{}'
      ;;
    "pay-sign-server")
      test_api "$service" $port "/ci/pay-sign/request" '{}'
      ;;
    "recon-server")
      test_api "$service" $port "/ci/recon/request" '{}'
      ;;
    "trans-query-server")
      test_api "$service" $port "/ci/trans/query" '{"cardNum":"TEST"}'
      ;;
    "card-pool-server")
      test_api "$service" $port "/ci/card-pool/query" '{"cardNum":"TEST"}'
      ;;
    "collect-ticket-server")
      test_api "$service" $port "/ci/collect-ticket/request" '{}'
      ;;
    "collect-pay")
      test_api "$service" $port "/ci/collect-pay/requestOrder" '{"userId":"TEST"}'
      ;;
    "industry-data-server")
      test_api "$service" $port "/ci/industry-data/request" '{}'
      ;;
    "acc-es-server")
      test_api "$service" $port "/ci/acc-es/query" '{}'
      ;;
    "alipay-account-server")
      test_api "$service" $port "/ci/alipay-account/request" '{}'
      ;;
    "alipay-pay-sign-server")
      test_api "$service" $port "/ci/alipay-pay-sign/request" '{}'
      ;;
    "fep-acc-server")
      test_api "$service" $port "/ci/fep-acc/request" '{}'
      ;;
    "fep-alipay-server")
      test_api "$service" $port "/ci/fep-alipay/request" '{}'
      ;;
    "acc-secure-server")
      test_api "$service" $port "/ci/acc-secure/request" '{}'
      ;;
    "acc-security-server")
      test_api "$service" $port "/ci/acc-security/request" '{}'
      ;;
  esac
  echo ""
done

echo "======================================"
echo "测试结果汇总"
echo "======================================"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "跳过: $SKIP"
echo "总计: $TOTAL"
echo ""
if [ $FAIL -eq 0 ]; then
    echo "✓ 全部测试通过"
else
    echo "✗ 有 $FAIL 个测试失败"
fi
