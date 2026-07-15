#!/bin/bash

# ==========================================
# 批量串行部署脚本 (有序列表版)
# 用法:
#   ./batch-deploy.sh              # 按预定义顺序部署全部服务
#   ./batch-deploy.sh svc1 svc2    # 仅部署指定服务
# ==========================================

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

# ✅ 在此维护你的全量部署清单（严格按此顺序执行）
ALL_SERVICES=(
    "account-server"
    "wallet-server"
    "acc-secure-server"
    "acc-security-server"
    "acc-es-server"
    "collect-ticket-server"
    "collect-pay-server"
    "pay-sign-server"
    "online-server"
    "ticket-server"
    "industry-data-server"
    "fep-app-server"
    "fep-dev-server"
    "gate-txn-pay-server"
    "blacklist-server"
    "key-server"
    "para-server"
)

# ---------- 确定本次部署列表 ----------
if [ $# -gt 0 ]; then
    SERVICES=("$@")
    MODE="指定部署"
else
    SERVICES=("${ALL_SERVICES[@]}")
    MODE="全量部署(预定义列表)"
fi

TOTAL=${#SERVICES[@]}
SUCCESS_COUNT=0
FAIL_COUNT=0
FAILED_LIST=""

echo -e "${BLUE}=========================================="
echo -e "🚀 开始串行部署 | 模式: ${MODE}"
echo -e "📋 服务列表(${TOTAL}): ${SERVICES[*]}"
echo -e "==========================================${NC}\n"

START_TIME=$(date +%s)

for SERVICE in "${SERVICES[@]}"; do
    echo -e "${YELLOW}------------------------------------------${NC}"
    echo -e "${BLUE}[STEP] ▶️  (${SUCCESS_COUNT}/${TOTAL}) 正在部署: ${SERVICE}${NC}"
    echo -e "${YELLOW}------------------------------------------${NC}"

    if ./deploy-to-harbor.sh "$SERVICE"; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        echo -e "\n${GREEN}[INFO] ✅ ${SERVICE} 部署成功!${NC}\n"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        FAILED_LIST="${FAILED_LIST} ${SERVICE}"
        echo -e "\n${RED}[ERROR] ❌ ${SERVICE} 部署失败! 终止后续部署.${NC}\n"
        break
    fi
done

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# ---------- 汇总报告 ----------
echo -e "${BLUE}=========================================="
echo -e "📊 部署汇总报告"
echo -e "==========================================${NC}"
echo -e "⏱️  总耗时: ${ELAPSED}s"
echo -e "✅ 成功: ${GREEN}${SUCCESS_COUNT}${NC}"
echo -e "❌ 失败: ${RED}${FAIL_COUNT}${NC}"
echo -e "📦 总计: ${TOTAL}"

if [ $FAIL_COUNT -gt 0 ]; then
    echo -e "\n${RED}⚠️  失败的服务:${FAILED_LIST}${NC}"
    echo -e "${YELLOW}💡 修复后可单独重跑: ./batch-deploy.sh${FAILED_LIST}${NC}"
    exit 1
else
    echo -e "\n${GREEN}🎉 所有服务串行部署完成!${NC}"
    exit 0
fi
