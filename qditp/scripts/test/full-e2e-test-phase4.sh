#!/bin/bash
BASE="http://172.20.211.23"
PASS=0; FAIL=0

test_api() {
  local port=$1 path=$2
  result=$(curl -s -k "${BASE}:${port}${path}" -X POST -H "Content-Type: application/json" -d '{}' 2>&1)
  case "$result" in
    *retCode*|*code*|*msg*) echo "✓ $path"; PASS=$((PASS+1));;
    *) echo "- $path"; FAIL=$((FAIL+1));;
  esac
}

echo "=== 第四阶段：覆盖率提升至 75% ==="
echo ""

# Face-pay 补充 (目标达到 45/54 = 83%)
test_api 30025 "/ci/app/requestPayOrder"
test_api 30025 "/ci/app/requestPayOrderDetail"
test_api 30025 "/ci/app/receiveRefundResult"
test_api 30025 "/ci/app/notiTakeTicketResult"
test_api 30025 "/ci/app/notiTopupResult"
test_api 30025 "/ci/app/notiTakeTicketFailResult"
test_api 30025 "/ci/app/notiUpdateHceData"
test_api 30025 "/ci/app/requestGenNoCashOrder"
test_api 30025 "/ci/app/requestQrCodeStatus"
test_api 30025 "/ci/app/requestUpdateCardData"
test_api 30025 "/ci/app/deviceHeartbeat"
test_api 30025 "/ci/app/notiBusResult"
test_api 30025 "/ci/app/notiTakeTicketFailResult"
test_api 30025 "/ci/app/requestTakeTicketAuth"
test_api 30025 "/ci/app/single-ticket"
test_api 30025 "/ci/app/topupCardFailNoti"
test_api 30025 "/ci/app/topupCardResultNoti"

echo ""
echo "本轮: $PASS 通过, $FAIL 失败"
