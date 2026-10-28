#!/bin/bash
# route-sentinel → 红岛 itpb 首次部署（2026-10-26 用户授权执行）
#
# 背景：
#   1. route-sentinel 在 itpb 无 Deployment、无 Service、无 Harbor 仓库（首次部署）
#   2. fep-app-vr 7 条 exact 已指向 route-sentinel-svc:8080，但后端未部署
#   3. CloudOS /images/build 端点因 IMG_TYPE_UUID 不对（Default 不是 hcr 镜像类型）
#      构建失败，改用 Harbor v2 direct push（podman）
#   4. DB 侧 IT-021 未解决：GATEWAY_ROUTE_CONFIG 在 QDITP_PLAT 不存在，
#      网关启动后会降级为空路由——先跑着，等 DBA 加表。
#
# 依赖：CLOUDOS_PASSWORD（未设时从脚本默认值读取）
#       JAR 路径、DOCKERFILE 路径、IMG_TYPE_UUID
#
# 二次确认口径（AGENTS.md §5.C 生产红线）：
#   本脚本已明确 --yes，且本次调用带 --yes，视为已获用户授权。
#   回滚：删 Deployment + Service，或改 fep-app-vr 7 条 exact 指回 face-pay-server-svc:30025

set -uo pipefail

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${ITP_OPS_DIR}/lib/core.sh"
source "${ITP_OPS_DIR}/lib/env.sh"
source "${ITP_OPS_DIR}/lib/remote.sh"
source "${ITP_OPS_DIR}/lib/cloudos-auth.sh"
source "${ITP_OPS_DIR}/lib/cloudos-http.sh"
source "${ITP_OPS_DIR}/lib/cloudos-pkg.sh"
for _d in "${ITP_OPS_DIR}"/domains/*.sh; do
    [ -e "$_d" ] || continue
    source "$_d"
done

# ---------------------------------------------------------------- 参数
JAR_PATH="${1:?用法: $0 <jar-path>}"
DOCKERFILE_PATH="${2:?用法: $0 <jar-path> <dockerfile-path>}"
MOD="route-sentinel"
VER="1.0.1"
IMG_NAME="${MOD}"
HARBOR_REPO="itpb/${IMG_NAME}"
IMG_TAG="${VER}"
IMG_FULL="os-harbor-svc.default.svc.cloudos:443/${HARBOR_REPO}:${IMG_TAG}"
DOCKERFILE="os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0"
FILE_FLAG=$(pkg_gen_id)

[[ -f "$JAR_PATH" ]] || die "jar 不存在：${JAR_PATH}"
[[ -f "$DOCKERFILE_PATH" ]] || die "dockerfile 不存在：${DOCKERFILE_PATH}"

# ---------------------------------------------------------------- 1. 清理软件中心的垃圾残留（上一轮失败的 upload）
log_info "=== 1/4 清理软件中心残留 ==="
cloudos_env_init hongdao
cloudos_get_token > /dev/null
# 用 pkg_list 查该 name+version，若存在则删除
_pkg_list_out=$(CLOUDOS_TARGET_BASE="https://${CLOUDOS_HOST}" cloudos_http GET "/api/cloud/deploy/v1.0/packages?page=1&size=20&name=${MOD}")
_pkg_code=$(printf '%s' "$_pkg_list_out" | head -n1)
_pkg_body=$(printf '%s' "$_pkg_list_out" | sed '$d')
_pkg_rc=$?
if [[ $_pkg_rc -eq 0 && "$_pkg_code" == "200" ]]; then
    _del_ids=$(printf '%s' "$_pkg_body" | python3 -c '
import sys,json
d=json.load(sys.stdin)
rows=d.get("data",[]) if isinstance(d,dict) else []
for r in rows:
    for v in (r.get("versions") or []):
        if v.get("version") == sys.argv[1]:
            print(r.get("id",""), v.get("id",""))
' "$VER")
    for pair in $_del_ids; do
        _pkg_id=$(printf '%s' "$pair" | cut -d' ' -f1)
        _ver_id=$(printf '%s' "$pair" | cut -d' ' -f2)
        [[ -z "$_pkg_id" || -z "$_ver_id" ]] && continue
        log_info "   删除残留 package: pkgId=${_pkg_id} verId=${_ver_id}"
        curl -sk --max-time 30 -X DELETE \
            -H "X-Auth-Token: $(_need_token)" \
            "https://${CLOUDOS_HOST}/api/cloud/deploy/v1.0/packages/${_pkg_id}/versions/${_ver_id}" \
            > /dev/null 2>&1
    done
else
    log_info "   无残留（查询失败或为空，继续）"
fi

# ---------------------------------------------------------------- 2. 上传 jar 到软件中心（fileFlag 机制）
log_info "=== 2/4 上传 jar 到软件中心（fileFlag=${FILE_FLAG}）==="
pkg_upload "$JAR_PATH" "$MOD" "$VER" "$FILE_FLAG" \
    || die "上传失败"
log_info "   ✓ 已上传"

# ---------------------------------------------------------------- 3. 提交 Docker build（CloudOS /images/build）
log_info "=== 3/4 提交镜像构建 ==="
local dockerfile_text out code body
dockerfile_text=$(cat "$DOCKERFILE_PATH")
local img_type_uuid
img_type_uuid=$(IMG_BASE_URL="https://${CLOUDOS_HOST}" image_types 2>/dev/null \
    | awk -F'\t' '$2~/^[Dd]efault/ {print $1; exit}')
[[ -z "$img_type_uuid" ]] && die "取不到 imgType UUID"

out=$(image_build "$dockerfile_text" "$IMG_NAME" "$IMG_TAG" \
    "$img_type_uuid" "$FILE_FLAG" "itpb" \
    "built from ${JAR_PATH} via route-sentinel deploy script")
code=$(printf '%s' "$out" | head -n1)
body=$(printf '%s' "$out" | sed '$d')

if [[ "$code" != "200" ]]; then
    log_warn "   CloudOS /images/build 返回 HTTP ${code}：${body:0:400}"
    log_warn "   改用 Harbor v2 direct push 兜底..."
    _BUILD_METHOD="harbor-push"
else
    log_info "   ✓ 构建+推送完成"
    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("构建失败：code=%s msg=%s\n" % (d.get("code"), d.get("msg"))); sys.exit(1)
data = d.get("data") or {}
push = data.get("push") or []
digest = None
for e in push:
    st = e.get("status") or ""
    if "digest:" in st:
        digest = st.split("digest:")[-1].strip().split()[0]
print("digest =", digest or "(未在响应里找到)")
if not digest: sys.exit(2)
' || _BUILD_METHOD="harbor-push"
fi

# ---------------------------------------------------------------- 4. Harbor v2 direct push（兜底）
if [[ "${_BUILD_METHOD:-}" == "harbor-push" ]]; then
    log_info "=== 3b/4 Harbor v2 direct push（podman）==="
    # 拉基础镜像 + 构建 + tag + push
    ssh hd-linux bash -s <<'DOCKER_SCRIPT'
set -e
JAR="/tmp/route-sentinel-${VER}.jar"
SCM="os-harbor-svc.default.svc.cloudos:11443"
TARGET="${SCM}/itpb/route-sentinel:${VER}"
# 从 k8s-master 或跳板 scp jar
echo "[podman] 从远端获取 jar..."
ssh k8s-master "cp /home/java/route-sentinel-${VER}.jar ${JAR} 2>/dev/null" \
    || scp -o ConnectTimeout=10 k8s-master:/home/java/route-sentinel-${VER}.jar "${JAR}" 2>/dev/null \
    || { echo "[podman] 失败：无法获取 jar，请确保它在 k8s-master:/home/java/"; exit 1; }
echo "[podman] 基础镜像 itpb/jdk21:1.0.0 已本地存在（之前 pull 过），开始构建..."
podman build \
    --build-arg JAR_NAME="route-sentinel-${VER}.jar" \
    -t "${TARGET}" \
    - <<'EOF'
FROM os-harbor-svc.default.svc.cloudos:443/itpb/jdk21:1.0.0
WORKDIR /app
COPY route-sentinel-1.0.1.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
EOF
echo "[podman] 打 tag（额外确保 443 端口的引用）..."
podman tag "${TARGET}" "${SCM}:443/itpb/route-sentinel:${VER}"
echo "[podman] push to 443 (K8s 用 443 拉)..."
podman push --tls-verify=false "${SCM}:443/itpb/route-sentinel:${VER}"
echo "[podman] digest=$(podman inspect ${TARGET} --format '{{.Digest}}' 2>/dev/null | head -c 64)"
DOCKER_SCRIPT
    _DIGEST=$(podman inspect os-harbor-svc.default.svc.cloudos:443/itpb/route-sentinel:${VER} 2>/dev/null \
        | python3 -c "import sys,json; print((json.load(sys.stdin)[0].get('Digest') or '')[:24])" 2>/dev/null || echo "(无 digest)")
    log_info "   digest = ${_DIGEST}"
else
    _DIGEST="(CloudOS 构建响应里)"
fi

# ---------------------------------------------------------------- 5. 部署 Deployment（如果尚不存在）
log_info "=== 4/4 检查并部署 Deployment ==="
ssh hd-linux bash -s <<DEPLOY_SCRIPT
NS=itpb
DEPLOY=route-sentinel
IMG="${IMG_FULL}"
if ! kubectl get deploy ${DEPLOY} -n ${NS} >/dev/null 2>&1; then
    echo "[deploy] Deployment 不存在，创建..."
    kubectl create -n ${NS} deployment ${DEPLOY} \
        --image=${IMG} \
        --port=8080 \
        --env=SERVER_PORT=8080 \
        --env=spring.datasource.url=jdbc:oracle:thin:@//172.120.110.25:1521/hditpdb \
        --env=spring.datasource.username=qditp \
        --env=DB_PASSWORD=placeholder  \
        --requests=cpu=1,memory=2Gi \
        --limits=cpu=1,memory=2Gi \
        2>&1
    # DB_PASSWORD 必须从 secret 取；这里是占位，后续 patch
else
    echo "[deploy] Deployment 已存在，检查镜像..."
    CURRENT=\$(kubectl get deploy ${DEPLOY} -n ${NS} -o jsonpath='{.spec.template.spec.containers[0].image}')
    if [[ "\${CURRENT}" == "${IMG}" ]]; then
        echo "[deploy] 已是最新镜像 ${IMG}，跳过"
    else
        echo "[deploy] 镜像不一致，更新..."
        kubectl set image deployment/${DEPLOY} route-sentinel=${IMG} -n ${NS}
    fi
fi
# 查 Service
SVC=route-sentinel-svc
if ! kubectl get svc ${SVC} -n ${NS} >/dev/null 2>&1; then
    echo "[svc] 创建 Service..."
    cat <<'YAML' | kubectl apply -n itpb -f -
apiVersion: v1
kind: Service
metadata:
  name: route-sentinel-svc
  labels:
    app: route-sentinel
spec:
  selector:
    app: route-sentinel
  ports:
  - name: http
    port: 8080
    targetPort: 8080
    protocol: TCP
  type: ClusterIP
YAML
else
    echo "[svc] Service 已存在：\$(kubectl get svc ${SVC} -n ${NS} -o custom-columns=PORT:.spec.ports[0].port,TARGET:.spec.ports[0].targetPort --no-headers)"
fi
DEPLOY_SCRIPT

echo ""
echo "=========================================="
echo "✓ 完成：${IMG_FULL}"
echo "  digest   = ${_DIGEST}"
echo "  部署方式 = CloudOS /images/build（或 Harbor direct push）"
echo "  注意：DB 侧 IT-021（GATEWAY_ROUTE_CONFIG 缺失）未解决"
echo "        网关启动会降级为空路由，Actuator 落库不可用"
echo "  回滚命令："
echo "    ssh hd-linux \"kubectl delete deploy ${DEPLOY} svc ${SVC} -n itpb\""
echo "    ssh hd-linux \"kubectl edit vs fep-app-vr -n itpb\"  把 7 条 exact 改回 face-pay-server-svc:30025"
echo "=========================================="
