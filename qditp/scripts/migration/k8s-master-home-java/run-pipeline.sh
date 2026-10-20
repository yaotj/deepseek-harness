#!/bin/bash
# ==========================================
# 统合流水线: 构建 + 推送 + K8s 部署 (E7501 适配版)
# 用法: 
#   ./run-pipeline.sh <服务名> <版本号>            # 完整流程
#   ./run-pipeline.sh <服务名> <版本号> skip-build  # 仅部署(跳过构建)
#   ./run-pipeline.sh <服务名> <版本号> skip-deploy # 仅构建(跳过部署)
# ==========================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE_NAME="${1:-}"
VERSION="${2:-}"
SKIP_FLAG="${3:-}"
PIPELINE_START=$(date +%s)

if [ -z "$SERVICE_NAME" ] || [ -z "$VERSION" ]; then
    echo "❌ 用法错误! 正确用法: $0 <服务名> <版本号> [skip-build|skip-deploy]"
    exit 1
fi

echo "======================================================"
echo "🔧 H3C CloudOS E7501 自动化流水线"
echo "   服务: $SERVICE_NAME | 版本: $VERSION | 模式: ${SKIP_FLAG:-full}"
echo "   启动时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "======================================================"

# ✅ 阶段1: 构建与推送
if [[ "$SKIP_FLAG" != "skip-build" ]]; then
    echo ""
    echo ">>> [1/2] 执行构建与推送..."
    if ! bash "$SCRIPT_DIR/build-push.sh" "$SERVICE_NAME" "$VERSION"; then
        echo "❌ 构建或推送失败，流水线终止！"
        exit 1
    fi
else
    echo ""
    echo ">>> [1/2] ⏭️  跳过构建与推送 (使用已有镜像)"
fi

# ✅ 阶段2: K8s 部署
if [[ "$SKIP_FLAG" != "skip-deploy" ]]; then
    echo ""
    echo ">>> [2/2] 执行 K8s 部署..."
    # k8s-deploy.sh 内部已包含 E7501 UI 提醒和审计日志写入
    if ! bash "$SCRIPT_DIR/k8s-deploy.sh" "$SERVICE_NAME" "$VERSION"; then
        echo "❌ K8s 部署失败，请检查上方详细日志！"
        exit 1
    fi
else
    echo ""
    echo ">>> [2/2] ⏭️  跳过 K8s 部署 (仅完成构建推送)"
fi

# ✅ 流水线耗时统计
PIPELINE_END=$(date +%s)
ELAPSED=$((PIPELINE_END - PIPELINE_START))

echo ""
echo "======================================================"
echo "🎉 流水线执行完毕!"
echo "   总耗时: ${ELAPSED}s | 服务: $SERVICE_NAME:$VERSION"
echo "======================================================"
