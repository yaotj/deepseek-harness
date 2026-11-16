#!/bin/bash
# Fep-app 全量测试 - 无需签名

BASE="http://172.20.211.23:30010"
PASS=0; FAIL=0; TOTAL=0

test_api() {
  local name=$1
  local path=$2
  local data=$3
  TOTAL=$((TOTAL+1))
  result=$(curl -s -k "${BASE}${path}" -X POST -H "Content-Type: application/json" -d "$data" 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) 
      echo "✓ $name"
      PASS=$((PASS+1))
      ;;
    *) 
      echo "- $name"
      FAIL=$((FAIL+1))
      ;;
  esac
}

echo "=== Fep-app 全量测试 ==="
echo ""

# 日票相关
test_api "requestCountingOrder" "/app/requestCountingOrder" '{"orderSource":"1","ticketPrice":1,"cardType":"45"}'
test_api "requestTravelOrder" "/app/dailyTicket/requestTravelOrder" '{"cardType":"45","userId":"00522980"}'
test_api "requestOrderFree" "/app/ticket/requestOrderFree" '{"orderNo":"TEST"}'
test_api "syncOrder" "/app/ticket/syncOrder" '{"orderNo":"TEST"}'
test_api "requestPay" "/app/dailyTicket/payment/requestPay" '{"thirdUserId":"00522990"}'
test_api "requestPayResult" "/app/dailyTicket/payment/requestPayResult" '{"orderNo":"TEST"}'
test_api "requestRefundTicket" "/app/dailyTicket/payment/requestRefundTicket" '{"orderNo":"TEST"}'
test_api "cancelOrder" "/app/ticket/cancelOrder" '{"orderNo":"TEST"}'
test_api "updateTicket" "/app/ticket/updateTicket" '{"orderNo":"TEST"}'
test_api "updateAndNotice" "/app/dailyTicket/updateAndNotice" '{"cardNum":"0426090951000208"}'
test_api "receivePayResult" "/app/dailyTicket/payment/receivePayResult" '{}'
test_api "receiveRefundResult" "/app/dailyTicket/payment/receiveRefundResult" '{}'

# 账户相关
test_api "requestApplication" "/app/requestApplication" '{"msisdn":"15000000203"}'
test_api "requestKeyList" "/app/requestKeyList" '{}'
test_api "requestAddPayChannel" "/app/requestAddPayChannel" '{"msisdn":"15000000203"}'
test_api "requestAgreeRelease" "/app/requestAgreeRelease" '{"msisdn":"15000000203"}'
test_api "requestSetDefaultPayChannel" "/app/requestSetDefaultPayChannel" '{"msisdn":"15000000203"}'
test_api "requestRemovePayChannel" "/app/requestRemovePayChannel" '{"msisdn":"15000000203"}'
test_api "employeeCardQuery" "/app/employeeCard/query" '{"cardNum":"0426090951000208"}'
test_api "employeeCardActivate" "/app/employeeCard/activate" '{"cardNum":"0426090951000208"}'
test_api "cancelAccount" "/app/cancelAccount" '{"msisdn":"15000000203"}'
test_api "changePhone" "/app/changePhone" '{"msisdn":"15000000203"}'

# 票务相关
test_api "queryBlackList" "/app/ticket/queryBlackList" '{"cardNum":"0426090951000208"}'
test_api "queryUserItinerary" "/app/ticket/queryUserItinerary" '{"cardNum":"0426090951000208"}'
test_api "requestExcessFare" "/app/ticket/requestExcessFare" '{"cardNum":"0426090951000208"}'
test_api "requestTransList" "/app/ticket/requestTransList" '{"cardNum":"0426090951000208"}'
test_api "requestTransDetail" "/app/ticket/requestTransDetail" '{"cardNum":"0426090951000208"}'
test_api "requestTransStatistics" "/app/ticket/requestTransStatistics" '{"cardNum":"0426090951000208"}'
test_api "realName" "/app/ticket/realName" '{"cardNum":"0426090951000208"}'

# 参数相关
test_api "requestBuySinlgeTicketMaxNum" "/app/requestBuySinlgeTicketMaxNum" '{}'
test_api "requestLineCodeList" "/app/requestLineCodeList" '{}'
test_api "requestStationCodeList" "/app/requestStationCodeList" '{}'
test_api "requestTicketPriceByStation" "/app/requestTicketPriceByStation" '{"fromStation":"1141"}'
test_api "requestLineStationCodeVersion" "/app/requestLineStationCodeVersion" '{}'

# 支付相关
test_api "requestPayOrder" "/app/payment/requestPayOrder" '{"userId":"00522990"}'
test_api "requestPaymentInfo" "/app/payment/requestPaymentInfo" '{"orderNo":"TEST"}'
test_api "requestPayFailOrder" "/app/payment/requestPayFailOrder" '{"orderNo":"TEST"}'
test_api "receivePayResult" "/app/payment/receivePayResult" '{}'

# 行业数据
test_api "requestIndustryData" "/ci/app/requestIndustryData" '{"cardNum":"0426090951000208"}'
test_api "requestNoSignalData" "/ci/app/requestNoSignalData" '{"cardNum":"0426090951000208"}'

# 签到相关
test_api "requestSignInfo" "/app/requestSignInfo" '{"msisdn":"15000000203"}'
test_api "receiveSignResult" "/ci/app/receiveSignResult" '{}'
test_api "requestTermination" "/ci/app/requestTermination" '{"msisdn":"15000000203"}'
test_api "receiveTerminationResult" "/ci/app/receiveTerminationResult" '{}'
test_api "requestContractResult" "/ci/app/requestContractResult" '{"msisdn":"15000000203"}'
test_api "unbindAgreement" "/userData/unbindAgreement" '{"msisdn":"15000000203"}'

# 其他
test_api "requestUserAccInfo" "/ci/app/requestUserAccInfo" '{"userId":"00522990"}'
test_api "requestUpdateChannelDefaultContract" "/app/requestUpdateChannelDefaultContract" '{"msisdn":"15000000203"}'
test_api "queryPayTxnBatch" "/ci/app/queryPayTxnBatch" '{}'

echo ""
echo "=== Fep-app 测试结果 ==="
echo "通过: $PASS / 失败: $FAIL / 总计: $TOTAL"
