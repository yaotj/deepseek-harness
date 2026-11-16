#!/bin/bash
# ITP 协议测试签名工具
PROVIDER_ID="01"
SECRET_KEY="test_secret_key_for_dev_only"
CHARSET="UTF-8"
FORMAT="json"

generate_sign() {
    local timestamp=$1
    local biz_data=$2
    local raw="${PROVIDER_ID}${CHARSET}${FORMAT}${timestamp}${SECRET_KEY}${biz_data}"
    echo -n "$raw" | md5sum | awk '{print $1}'
}

TIMESTAMP=$(date +%Y%m%d%H%M%S)
BIZ_DATA="${1:-{}}"
SIGN=$(generate_sign "$TIMESTAMP" "$BIZ_DATA")

echo "providerId=$PROVIDER_ID"
echo "charset=$CHARSET"
echo "format=$FORMAT"
echo "timestamp=$TIMESTAMP"
echo "signType=00"
echo "sign=$SIGN"
echo "bizData=$BIZ_DATA"
