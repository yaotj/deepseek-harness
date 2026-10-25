#!/bin/bash
# ==========================================
# K8s 纯部署脚本 (E7501 适配版)
# 用法: ./k8s-deploy.sh <服务名或JAR前缀> <版本号> [命名空间]
# ==========================================
set -euo pipefail

# 🎯 【配置区】特殊服务映射表 & 审计日志路径
declare -A JAR_MAP=(
    ["fep-app"]="fep-app-server"
)
# ✅ 审计日志路径已更新至 /home/java
AUDIT_LOG="/home/java/k8s-deploy-audit.csv"

INPUT_NAME="${1:-}"
VERSION="${2:-}"
NAMESPACE="${3:-itp}"

if [ -z "$INPUT_NAME" ] || [ -z "$VERSION" ]; then
    echo "❌ 用法错误! 正确用法: $0 <服务名或JAR前缀> <版本号> [命名空间]"
    exit 1
fi

# 💡 智能推导 Deployment 名称
DEPLOY_NAME="$INPUT_NAME"
if [[ -v "JAR_MAP[$INPUT_NAME]" ]]; then
    DEPLOY_NAME="${JAR_MAP[$INPUT_NAME]}"
else
    for key in "${!JAR_MAP[@]}"; do
        if [[ "${JAR_MAP[$key]}" == "$INPUT_NAME" ]]; then
            echo "💡 自动纠正部署名: $INPUT_NAME → $key"
            DEPLOY_NAME="$key"
            break
        fi
    done
fi

REGISTRY="os-harbor-svc.default.svc.cloudos:443/itp"
IMAGE_FULL="${REGISTRY}/${DEPLOY_NAME}:${VERSION}"

echo "=========================================="
echo "🚀 K8s 部署: $DEPLOY_NAME v$VERSION"
echo "🐳 镜像: $IMAGE_FULL"
echo "📍 命名空间: $NAMESPACE"
echo "=========================================="

# 🔍 检查 Deployment 是否存在
if ! kubectl get deployment "$DEPLOY_NAME" -n "$NAMESPACE" >/dev/null 2>&1; then
    echo "❌ Deployment '$DEPLOY_NAME' 在 '$NAMESPACE' 中不存在!"
    exit 1
fi

# 💡 动态获取真实容器名
CONTAINER_NAME=$(kubectl get deployment "$DEPLOY_NAME" -n "$NAMESPACE" \
  -o jsonpath='{.spec.template.spec.containers[0].name}')

if [ -z "$CONTAINER_NAME" ]; then
    echo "❌ 无法获取容器名，请检查 Deployment YAML 配置"
    exit 1
fi

# 🔄 执行滚动更新（分两步：先更新镜像，再记录变更原因）
echo "🔄 开始滚动更新 (容器: $CONTAINER_NAME)..."
kubectl set image deployment/"$DEPLOY_NAME" "$CONTAINER_NAME=$IMAGE_FULL" -n "$NAMESPACE"

# ✅ 单独添加变更注解（兼容 K8s v1.28）
kubectl annotate deployment/"$DEPLOY_NAME" \
  kubernetes.io/change-cause="Deployed ${VERSION} via k8s-deploy.sh at $(date '+%Y-%m-%d %H:%M:%S')" \
  -n "$NAMESPACE" --overwrite

# ⏳ 等待 Pod 就绪并捕获状态
DEPLOY_STATUS="SUCCESS"
echo "⏳ 等待 Pod 就绪 (最多 300 秒)..."
if ! kubectl rollout status deployment/"$DEPLOY_NAME" -n "$NAMESPACE" --timeout=300s; then
    DEPLOY_STATUS="FAILED"
    echo "❌ 升级超时或失败! 请使用 kubectl describe pod -n $NAMESPACE -l app=$DEPLOY_NAME 排查"
fi

# 📝 写入本地审计台账
mkdir -p "$(dirname "$AUDIT_LOG")"
echo "$(date '+%Y-%m-%d %H:%M:%S'),${DEPLOY_NAME},${NAMESPACE},${VERSION},${DEPLOY_STATUS}" >> "$AUDIT_LOG"

# ✅ 最终输出结果
if [ "$DEPLOY_STATUS" == "SUCCESS" ]; then
    echo ""
    echo "🎉 部署完成: $DEPLOY_NAME (ns=$NAMESPACE)"
    
    # ⚠️ E7501 专属提示块
    cat << EOF

========================================
⚠️  【E7501 平台提醒】
    CloudOS by DCM E7501 不支持 UI 版本自动同步。
    K8s 实际已更新至: ${VERSION}
    但 DCM 控制台显示可能仍为旧版本。

👉 如需修正 UI，请手动操作：
   DCM 控制台 → 企业应用 → ${DEPLOY_NAME}
   将「当前应用版本」修改为: ${VERSION}

💡 运维准则：以 K8s 实际运行版本为准，UI 仅作展示参考
📋 审计记录已追加至: ${AUDIT_LOG}
========================================
EOF
else
    exit 1
fi
