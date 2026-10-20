#!/bin/bash
# ==========================================
# JAR包镜像构建与推送自动化脚本 (智能纠错版)
# 用法: ./build-push.sh <服务名或JAR前缀> <版本号>
# 示例: 
#   ./build-push.sh fep-app 1.5        # ✅ 标准用法
#   ./build-push.sh fep-app-server 1.5 # ✅ 自动纠正为 fep-app
# ==========================================

set -e

# 🎯 【配置区】特殊服务映射表
# 格式: ["K8s部署名/镜像名"]="实际JAR包前缀"
declare -A JAR_MAP=(
    ["fep-app"]="fep-app-server"
    # 后续如有其他特殊情况，直接按此格式追加即可
)

# 1. 参数校验
if [ -z "$1" ] || [ -z "$2" ]; then
    echo "❌ 用法错误! 正确用法: $0 <服务名或JAR前缀> <版本号>"
    exit 1
fi

INPUT_NAME="$1"
VERSION="$2"

# 2. 智能推导：支持正向查表 + 反向纠错
IMAGE_PREFIX="$INPUT_NAME"
JAR_PREFIX="$INPUT_NAME"

if [[ -v "JAR_MAP[$INPUT_NAME]" ]]; then
    # 正向命中：传入的是 K8s 部署名 (如 fep-app)
    IMAGE_PREFIX="$INPUT_NAME"
    JAR_PREFIX="${JAR_MAP[$INPUT_NAME]}"
else
    # 反向查找：传入的可能是 JAR 前缀 (如 fep-app-server)
    for key in "${!JAR_MAP[@]}"; do
        if [[ "${JAR_MAP[$key]}" == "$INPUT_NAME" ]]; then
            echo "💡 检测到输入为JAR前缀，已自动纠正: $INPUT_NAME → $key"
            IMAGE_PREFIX="$key"
            JAR_PREFIX="$INPUT_NAME"
            break
        fi
    done
fi

JAR_FILE="${JAR_PREFIX}-${VERSION}.jar"
REGISTRY="os-harbor-svc.default.svc.cloudos:443/itp"
IMAGE_FULL="${REGISTRY}/${IMAGE_PREFIX}:${VERSION}"
WORK_DIR="/home/java"

# 3. 检查 JAR 包是否存在
cd "$WORK_DIR" || { echo "❌ 目录不存在: $WORK_DIR"; exit 1; }
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ 错误: 未找到目标JAR包 [$WORK_DIR/$JAR_FILE]"
    echo "   当前目录下的JAR文件:"
    ls -lh *.jar 2>/dev/null || echo "   (无)"
    exit 1
fi

echo "=========================================="
echo "🚀 开始构建: $IMAGE_FULL"
echo "📦 使用JAR包: $JAR_FILE"
echo "=========================================="

# 4. 清理无关 JAR 包 (防止 Dockerfile COPY 错文件)
echo "🧹 清理无关JAR包..."
find . -maxdepth 1 -name "*.jar" ! -name "$JAR_FILE" -delete

# 5. 构建镜像 (强制不使用缓存，确保每次都是最新JAR)
echo "🔨 正在构建镜像..."
docker build --no-cache -t "$IMAGE_FULL" .

# 6. 校验镜像是否构建成功
echo "🔍 校验镜像..."
if ! docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${IMAGE_FULL}$"; then
    echo "❌ 镜像构建失败!"
    exit 1
fi
echo "✅ 镜像构建成功!"

# 7. 推送镜像
echo "📤 正在推送到 Harbor..."
docker push "$IMAGE_FULL"

echo "=========================================="
echo "🎉 全部完成! 镜像已推送: $IMAGE_FULL"
echo "=========================================="
