#!/bin/bash
# ============================================================================
# hongdao-buildx —— 红岛 CloudOS 镜像构建（buildx 端点，platform=linux/amd64）
#
# 【为什么不用 itp-ops.sh cloudos image build】
#   旧命令走 /api/cloud/hcr/v1.0/images/build（红岛 404 / img.5，见 IT-024）。
#   红岛可用的构建通道是 Web 控制台「镜像构建」背后的 buildx 端点（2026-10-26 从
#   /apps-image SPA JS 反解确认，见 IT-024 §1.3）：
#     POST /api/cloud/hcr/v1.0/images/file/upload   multipart 上传构建上下文
#         fileFlag 走请求头（客户端自生成 uuid），响应 data.id
#     POST /api/cloud/hcr/v1.0/images/buildx        body 含 dockerfile/imgName/
#         version/imgType/type/fileFlag/project/platform/description
#   平台 x86 节点 buildx 构建并推 Harbor（:443）；platform=linux/amd64 可指定架构
#   （推翻 /images/build 时代「架构不可指定」的旧口径）。
#
# 【构建上下文形态（2026-10-28 实证变更）】
#   直接上传 jar 文件本身（文件名 app.jar），NEVER 打 tar——tar 上下文现一律
#   回 img.5（status=false）。route-sentinel 1.0.1（2026-10-26）时代 tar 可用，
#   平台行为已变，与 Web 端 el-upload 直传文件的形态对齐后恢复。
#
# 【用法】
#   hongdao-buildx.sh <module> <version> [--env hongdao|test] [--jar <jar>] \
#       [--dockerfile <df>] [--project <name>] [--img-type <uuid>] [--check]
#   --env     环境（默认 hongdao）。test 时 Harbor 项目自动取 itp、账号 admin；
#             文件名保留红岛首发时的历史名（改名会让 8+ 处 IT/手册引用失真），
#             脚本本身已按环境参数化，两个环境共用同一份实现。
#   --check   只读：只查现有镜像（确认 tag 占用 / 回滚点 / 响应形态），不发写请求
#
# 【纪律】
#   · 版本末位 MUST +0.0.1，NEVER 同 tag 覆盖（否则无法回滚）。
#   · 镜像 MUST 是 x86(amd64)：红岛业务节点是 x86；测试节点同为 x86，buildx 显式
#     带 platform=linux/amd64，构建后核 osAndArch（IT-024 §5）。
#   · 测试环境 buildx 路由同样存在（2026-11-02 实测：POST /images/buildx 空体回
#     img.5 参数错而非 404），与红岛是同一套端点，无需另写实现。
# ============================================================================

set -uo pipefail

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${ITP_OPS_DIR}/lib/core.sh"
source "${ITP_OPS_DIR}/lib/env.sh"
source "${ITP_OPS_DIR}/lib/cloudos-auth.sh"
source "${ITP_OPS_DIR}/lib/cloudos-http.sh"

# 默认 imgType：hcr 内置「Default」（2026-11-02 现查：红岛与测试该 uuid **恰好相同**）。
# 测试环境另有 ITP=21d45c2e-6b58-4179-bff8-c340b25a、ticket=c1c0a454-… 等分类，
# 要打别的分类用 --img-type 显式指定，NEVER 靠猜。
IMG_TYPE_DEFAULT="20e68cab-c634-468d-8d0c-56cb6096"

# 基础镜像按环境取（**环境事实，不是模块事实**）。Dockerfile 里写的是红岛值，
# 下面会在读取后用 awk 替换首行 FROM，所以共享模板仍然只有一份。
#   红岛：itpb/jdk21:1.0.0（2026-10-28 起全量 23 个镜像用它）
#   测试：itp/jdk:25     （2026-11-02 从 k8s-master:/home/java/Dockerfile 现查，
#                        当前测试全量镜像都基于它；itp/jdk21:1.0.0 在测试 Harbor **不存在**）
# 两套基础镜像的入口契约相同：ENTRYPOINT=`/bin/sh -c "sh /startapp.sh"`，
# 容器内均有 /startapp.sh 与 /home/javaapp/app/logs —— 故「NEVER 写 ENTRYPOINT」纪律通用。
BASE_IMAGE_HONGDAO="os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0"
BASE_IMAGE_TEST="os-harbor-svc.default.svc.cloudos:443/itp/jdk:25"

MOD="${1:?用法: $0 <module> <version> [--env hongdao|test] [--jar <jar>] [--dockerfile <df>] [--project <name>] [--img-type <uuid>] [--base-image <ref>] [--check]}"
VER="${2:?用法: $0 <module> <version> ...}"
shift 2

ENV_NAME="hongdao"
JAR=""; DOCKERFILE=""; PROJECT_ARG=""; IMG_TYPE="$IMG_TYPE_DEFAULT"
BASE_IMAGE_ARG=""; CHECK_ONLY=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)        ENV_NAME="${2:?--env 需要值（hongdao|test）}"; shift 2 ;;
        --jar)        JAR="$2"; shift 2 ;;
        --dockerfile) DOCKERFILE="$2"; shift 2 ;;
        --project)    PROJECT_ARG="$2"; shift 2 ;;
        --img-type)   IMG_TYPE="$2"; shift 2 ;;
        --base-image) BASE_IMAGE_ARG="$2"; shift 2 ;;
        --check)      CHECK_ONLY=1; shift ;;
        *) die "未知参数：$1" ;;
    esac
done

# 环境 profile 先应用，Harbor 项目 / 账号 / VIP 全部由它决定（--project 仅作覆盖）
ITP_ENV="$ENV_NAME"; ITP_ENV_EXPLICIT=1
itp_env_init "$ENV_NAME" || exit 1
PROJECT="${PROJECT_ARG:-$HARBOR_PROJECT}"
case "$ITP_ENV" in
    test) BASE_IMAGE="${BASE_IMAGE_ARG:-$BASE_IMAGE_TEST}" ;;
    *)    BASE_IMAGE="${BASE_IMAGE_ARG:-$BASE_IMAGE_HONGDAO}" ;;
esac
cloudos_env_init || exit 1
printf '[环境] %s（%s / %s / 账号 %s）  Harbor项目=%s  imgType=%s\n' \
    "$ITP_ENV_LABEL" "$CLOUDOS_ENV" "$CLOUDOS_HOST" "$CLOUDOS_USERNAME" "$PROJECT" "$IMG_TYPE" >&2
printf '       基础镜像=%s\n' "$BASE_IMAGE" >&2

TOKEN=$(cloudos_get_token) || die "登录失败"
BASE="${CLOUDOS_BASE_URL}"

# ---------------------------------------------------------------- 只读检查
if [[ "$CHECK_ONLY" -eq 1 ]]; then
    echo "[check] ${MOD} 现有镜像（project=${PROJECT}）" >&2
    curl -sSk --max-time 30 -H "X-Auth-Token: ${TOKEN}" \
        "${BASE}/api/cloud/hcr/v1.0/image/versions?name=${MOD}&harborProjectName=${PROJECT}&page=1&size=20" \
        | python3 -m json.tool
    exit 0
fi

# ---------------------------------------------------------------- 准备 jar + Dockerfile
[[ -n "$JAR" ]] || JAR="${ITP_OPS_ROOT:-$ITP_OPS_DIR/../..}/${MOD}/target/${MOD}-${VER}.jar"
[[ -f "$JAR" ]] || die "找不到 jar：${JAR}"
[[ -n "$DOCKERFILE" ]] || DOCKERFILE="${ITP_OPS_ROOT:-$ITP_OPS_DIR/../..}/${MOD}/Dockerfile"
[[ -f "$DOCKERFILE" ]] || die "找不到 Dockerfile：${DOCKERFILE}"

# 首行 FROM 换成环境对应的基础镜像（共享模板只写红岛值，替换在此处做，见文件头）
DOCKERFILE_RAW=$(cat "$DOCKERFILE")
DOCKERFILE_TEXT=$(printf '%s\n' "$DOCKERFILE_RAW" | awk -v img="$BASE_IMAGE" '
    !done && /^[[:space:]]*FROM[[:space:]]/ { print "FROM " img; done=1; next }
    { print }
')
FROM_IN_FILE=$(printf '%s\n' "$DOCKERFILE_RAW" | awk '/^[[:space:]]*FROM[[:space:]]/ {print $2; exit}')
[[ -n "$FROM_IN_FILE" ]] || die "${DOCKERFILE} 里没有 FROM 行，无法确定基础镜像"
[[ "$FROM_IN_FILE" == "$BASE_IMAGE" ]] \
    || echo "   基础镜像替换：${FROM_IN_FILE} → ${BASE_IMAGE}" >&2

# fat jar 校验
manifest=$(unzip -l "$JAR" 2>/dev/null) || die "${JAR} 不是合法 zip"
printf '%s' "$manifest" | grep -q "BOOT-INF/classes" \
    || die "${JAR} 不是 spring-boot fat jar（无 BOOT-INF/classes），平台无法 java -jar 启动"

# 构建上下文：直接上传 jar 文件本身（文件名 MUST 为 app.jar，对齐 Dockerfile 的 COPY app.jar）。
# 【2026-10-28 行为变化】历史上传 tar 包（tar 内含 app.jar）曾成功（route-sentinel 1.0.1），
# 现平台对 tar 上下文一律回 img.5（status=false），改为仿 Web 端 el-upload 直接传文件后即恢复。
STAGE=$(mktemp -d)
cp "$JAR" "${STAGE}/app.jar"
CONTEXT_FILE="${STAGE}/app.jar"
echo "[1/4] 构建上下文文件：${CONTEXT_FILE}（$(stat -f %z "$CONTEXT_FILE" 2>/dev/null || stat -c %s "$CONTEXT_FILE") bytes，直接上传不打 tar）" >&2

# ---------------------------------------------------------------- 1) file/upload
FILEFLAG=$(python3 -c 'import uuid;print(uuid.uuid4().hex)')
echo "[2/4] file/upload（fileFlag=${FILEFLAG}）" >&2
UP=$(curl -sSk --max-time 300 -H "X-Auth-Token: ${TOKEN}" -H "fileFlag: ${FILEFLAG}" \
    -F "file=@${CONTEXT_FILE}" \
    "${BASE}/api/cloud/hcr/v1.0/images/file/upload")
echo "   upload 响应：$(printf '%s' "$UP" | head -c 300)" >&2
# 注：该端点成功时 data 为 ""（仅 ack），fileFlag 由客户端自生成、直接作为 buildx 引用
UP_OK=$(printf '%s' "$UP" | python3 -c '
import sys,json
try:
    d=json.load(sys.stdin)
except Exception as e:
    sys.stderr.write("upload 响应非 JSON：%s\n"%e); sys.exit(1)
if d.get("status") is not True:
    sys.stderr.write("upload 失败：code=%s msg=%s\n"%(d.get("code"),d.get("msg"))); sys.exit(1)
print("ok")
' ) || die "file/upload 失败"
DATA_ID="$FILEFLAG"
echo "   ✓ 用客户端 fileFlag=${DATA_ID} 作为 buildx 引用" >&2

# ---------------------------------------------------------------- 2) buildx
BODY=$(python3 -c '
import json,sys
print(json.dumps({
  "dockerfile": sys.argv[1],
  "imgName": sys.argv[2],
  "version": sys.argv[3],
  "imgType": sys.argv[4],
  "type": "private",
  "fileFlag": sys.argv[5],
  "project": sys.argv[6],
  "platform": "linux/amd64",
  "description": sys.argv[2] + " " + sys.argv[3] + " via cloudos buildx (" + sys.argv[7] + ")"
}, ensure_ascii=False))' "$DOCKERFILE_TEXT" "$MOD" "$VER" "$IMG_TYPE" "$DATA_ID" "$PROJECT" "$CLOUDOS_ENV")

echo "[3/4] buildx 提交构建（platform=linux/amd64）" >&2
BX=$(curl -sSk --max-time 120 -H "X-Auth-Token: ${TOKEN}" \
    -H "Content-Type: application/json" -X POST -d "$BODY" \
    "${BASE}/api/cloud/hcr/v1.0/images/buildx")
echo "   buildx 响应：$(printf '%s' "$BX" | head -c 600)" >&2

# ---------------------------------------------------------------- 3) 轮询确认产物
echo "[4/4] 轮询镜像仓库（最多 120s），确认 ${PROJECT}/${MOD}:${VER} linux/amd64" >&2
OK=""
for i in $(seq 1 12); do
    sleep 10
    RESP=$(curl -sSk --max-time 30 -H "X-Auth-Token: ${TOKEN}" \
        "${BASE}/api/cloud/hcr/v1.0/image/versions?name=${MOD}&harborProjectName=${PROJECT}&page=1&size=20")
    LINE=$(printf '%s' "$RESP" | python3 -c '
import sys,json
try:
    d=json.load(sys.stdin)
except Exception:
    print(""); sys.exit(0)
# 响应形态（2026-10-28 现查）：data 是 dict，镜像列表在 data.images[]，
# 每条记录的版本字段叫 versions（多 tag 时以逗号串在一起），NEVER 按 data[] + version 解析。
data = d.get("data") or {}
for grp in (data.get("images") if isinstance(data, dict) else data) or []:
    if grp.get("imgName") != sys.argv[1]:
        continue
    tags = [t.strip() for t in str(grp.get("versions") or "").split(",")]
    if sys.argv[2] in tags:
        print("%s|%s|%s" % (grp.get("imgUrl"), grp.get("osAndArch"), grp.get("digest")))
        sys.exit(0)
print("")
' "$MOD" "$VER" 2>/dev/null)
    if [[ -n "$LINE" ]]; then
        OK="$LINE"; echo "   ✓ 第 ${i} 次探测命中：${LINE}" >&2; break
    fi
    printf '   … 第 %d 次探测未命中，继续\n' "$i" >&2
done

rm -rf "$STAGE"
if [[ -z "$OK" ]]; then
    echo "   ⚠ 轮询超时：构建可能仍在进行或失败，去 CloudOS 控制台「镜像」页核对 ${PROJECT}/${MOD}:${VER}" >&2
    exit 2
fi

IMG_URL=$(printf '%s' "$OK" | cut -d'|' -f1)
ARCH=$(printf '%s' "$OK" | cut -d'|' -f2)
DIGEST=$(printf '%s' "$OK" | cut -d'|' -f3)
echo ""
echo "=========================================="
echo "✓ 构建完成：${IMG_URL}"
echo "  arch   = ${ARCH}"
echo "  digest = ${DIGEST}"
echo "  回滚点 = ${PROJECT}/${MOD}:上一个 tag（Harbor 未删）"
echo "=========================================="
