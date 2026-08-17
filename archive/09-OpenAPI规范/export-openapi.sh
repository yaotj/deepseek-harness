#!/bin/bash
# 导出支付宝相关服务 OpenAPI 文档
# 使用方法: bash export-openapi.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCS_DIR="${SCRIPT_DIR}/docs/openapi"
mkdir -p "${DOCS_DIR}"

# 服务配置数组：服务名:端口:输出文件名
services=(
  "fep-alipay-server:8080:fep-alipay-server"
  "alipay-account-server:9106:alipay-account-server"
  "alipay-pay-sign-server:8080:alipay-pay-sign-server"
)

echo "开始导出 OpenAPI 文档..."
echo "----------------------------------------"

for service in "${services[@]}"; do
  IFS=':' read -r name port output <<< "$service"
  url="http://localhost:${port}/v3/api-docs"
  output_file="${DOCS_DIR}/${output}-openapi.json"

  echo "正在导出 [${name}] => ${output_file}"
  echo "  地址: ${url}"

  if curl -s -f "${url}" -o "${output_file}"; then
    # 校验 JSON 格式
    if python3 -c "import json; json.load(open('${output_file}'))" 2>/dev/null; then
      echo "  ✓ 导出成功"
    else
      echo "  ✗ 文件格式异常，请检查服务是否正常启动"
      rm -f "${output_file}"
    fi
  else
    echo "  ✗ 导出失败，请检查服务是否已启动在端口 ${port}"
  fi
  echo ""
done

echo "----------------------------------------"
echo "导出完成，文件保存在: ${DOCS_DIR}"
echo ""
echo "可通过以下方式查看文档:"
echo "  1. 启动服务后访问: http://localhost:<端口>/doc.html"
echo "  2. 或使用 curl 直接获取: curl http://localhost:<端口>/v3/api-docs"
