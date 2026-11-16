#!/bin/bash
# 测试更多服务

BASE="http://172.20.211.23"
PASS=0; FAIL=0

test_api() {
  local service=$1 port=$2 path=$3
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) 
      echo "✓ $service $path"
      PASS=$((PASS+1))
      ;;
    *) 
      echo "- $service $path"
      FAIL=$((FAIL+1))
      ;;
  esac
}

echo "=== 补充测试更多服务 ==="
echo ""

# Blacklist-server
test_api "blacklist-server" 30015 "/ci/blacklist/query"
test_api "blacklist-server" 30015 "/ci/blacklist/add"
test_api "blacklist-server" 30015 "/ci/blacklist/remove"

# Key-server
test_api "key-server" 30016 "/ci/key/request"
test_api "key-server" 30016 "/ci/key/update"

# Para-server
test_api "para-server" 30017 "/ci/para/requestLineCodeList"
test_api "para-server" 30017 "/ci/para/requestStationCodeList"
test_api "para-server" 30017 "/ci/para/requestTicketPriceByStation"

# Pay-sign-server
test_api "pay-sign-server" 30018 "/ci/pay-sign/requestSignInfo"
test_api "pay-sign-server" 30018 "/ci/pay-sign/receiveSignResult"

# Recon-server
test_api "recon-server" 30020 "/ci/recon/requestFileList"
test_api "recon-server" 30020 "/ci/recon/processFile"

# Trans-query-server
test_api "trans-query-server" 30021 "/ci/trans/query"
test_api "trans-query-server" 30021 "/ci/trans/detail"

# Card-pool-server
test_api "card-pool-server" 30022 "/ci/card-pool/query"
test_api "card-pool-server" 30022 "/ci/card-pool/activate"

# Collect-ticket-server
test_api "collect-ticket-server" 30023 "/ci/collect-ticket/request"

# Industry-data-server
test_api "industry-data-server" 30026 "/ci/industry-data/request"

# Acc-es-server
test_api "acc-es-server" 30028 "/ci/acc-es/query"

# Alipay services
test_api "alipay-account-server" 30029 "/ci/alipay-account/request"
test_api "alipay-pay-sign-server" 30030 "/ci/alipay-pay-sign/request"

# Fep services
test_api "fep-acc-server" 30031 "/ci/fep-acc/request"
test_api "fep-alipay-server" 30033 "/ci/fep-alipay/request"

# Acc secure/security
test_api "acc-secure-server" 30034 "/ci/acc-secure/request"
test_api "acc-security-server" 30035 "/ci/acc-security/request"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败"
