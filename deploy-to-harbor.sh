#!/bin/bash

# ==========================================
# 配置区域
# ✅ 部署全部服务（按 ALL_SERVICES 数组顺序）
#./deploy-to-harbor.sh
# ✅ 只部署单个服务
#./deploy-to-harbor.sh fep-dev-server
# ✅ 同时部署多个指定服务
#./deploy-to-harbor.sh acc-es-server ticket-server key-server
# ==========================================
REMOTE_HOST="k8s-master"
REMOTE_DIR="/home/java"
LOCAL_BASE_DIR="/Users/tuanjie/workspace/chinasofti/qd/qditp"

# 全量服务列表（不传参时按此顺序部署）
ALL_SERVICES=(
    "fep-dev-server"
    "industry-data-server"
    "gate-txn-pay-server"
    "acc-es-server"
    "pay-sign-server"
    "ticket-server"
    "fep-app-server"
    "blacklist-server"
    "key-server"
    "account-server"
)

# ==========================================
# 颜色与日志
# ==========================================
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${CYAN}[STEP]${NC} $1"; }

# ==========================================
# 从JAR文件名中提取版本号
# ==========================================
extract_version() {
    local jar_name="$1"
    local service_name="$2"
    local version="${jar_name%.jar}"
    version="${version#${service_name}-}"
    echo "$version"
}

# ==========================================
# 部署单个服务的函数
# ==========================================
deploy_service() {
    local SERVICE_NAME="$1"

    log_step "▶️  处理服务: ${SERVICE_NAME}"

    local TARGET_DIR="${LOCAL_BASE_DIR}/${SERVICE_NAME}/target"

    # 1. 检查 target 目录
    if [ ! -d "$TARGET_DIR" ]; then
        log_error "target 目录不存在: $TARGET_DIR"
        return 1
    fi

    # 2. 查找最新 JAR（排除 non-runnable 包）
    local LATEST_JAR
    LATEST_JAR=$(find "$TARGET_DIR" -maxdepth 1 -name "${SERVICE_NAME}-*.jar" \
        ! -name "*-original.jar" \
        ! -name "*-sources.jar" \
        ! -name "*-javadoc.jar" \
        -type f -print0 | xargs -0 ls -t 2>/dev/null | head -n 1)

    if [ -z "$LATEST_JAR" ]; then
        log_error "未找到匹配的JAR: ${TARGET_DIR}/${SERVICE_NAME}-*.jar"
        return 1
    fi

    local JAR_FILENAME
    JAR_FILENAME=$(basename "$LATEST_JAR")
    local VERSION
    VERSION=$(extract_version "$JAR_FILENAME" "$SERVICE_NAME")

    # 3. 版本号安全校验
    if [ -z "$VERSION" ] || [ "$VERSION" = "$JAR_FILENAME" ]; then
        log_error "无法从 ${JAR_FILENAME} 中提取版本号!"
        return 1
    fi

    log_info "📄 JAR文件: ${JAR_FILENAME}"
    log_info "🏷️  版本号:  ${VERSION}"

    # 4. SCP 上传
    log_info "⬆️  正在上传到 ${REMOTE_HOST}:${REMOTE_DIR}/ ..."
    if ! scp "$LATEST_JAR" "${REMOTE_HOST}:${REMOTE_DIR}/"; then
        log_error "上传失败: ${JAR_FILENAME}"
        return 1
    fi

    # 5. SSH 远程构建推送
    log_info "🔨 正在远程执行: ./build-push.sh ${SERVICE_NAME} ${VERSION}"
    if ! ssh "${REMOTE_HOST}" "cd ${REMOTE_DIR} && ./build-push.sh ${SERVICE_NAME} ${VERSION}"; then
        log_error "构建推送失败: ${SERVICE_NAME}:${VERSION}"
        return 1
    fi

    log_info "✅ ${SERVICE_NAME}:${VERSION} 部署完成!"
    return 0
}

# ==========================================
# 参数解析 & 主入口
# ==========================================
set -e

# 确定要部署的服务列表
if [ $# -eq 0 ]; then
    # 无参数 → 部署全部
    DEPLOY_LIST=("${ALL_SERVICES[@]}")
    log_info "📦 未指定参数，将部署全部 ${#DEPLOY_LIST[@]} 个服务..."
else
    # 有参数 → 只部署指定的服务（支持多个: ./deploy.sh svc1 svc2）
    DEPLOY_LIST=("$@")
    log_info "📦 指定部署 ${#DEPLOY_LIST[@]} 个服务: ${DEPLOY_LIST[*]}"
fi

echo "=========================================="

SUCCESS_COUNT=0
FAIL_COUNT=0

for svc in "${DEPLOY_LIST[@]}"; do
    echo ""
    if deploy_service "$svc"; then
        ((SUCCESS_COUNT++))
    else
        log_warn "服务 ${svc} 部署失败，继续下一个..."
        ((FAIL_COUNT++))
    fi
done

# ==========================================
# 汇总报告
# ==========================================
echo ""
echo "=========================================="
log_info "🎉 部署结束! 成功: ${SUCCESS_COUNT}, 失败: ${FAIL_COUNT}, 总计: ${#DEPLOY_LIST[@]}"
if [ $FAIL_COUNT -gt 0 ]; then
    log_warn "有 ${FAIL_COUNT} 个服务部署失败，请检查上方日志"
    exit 1
fi
