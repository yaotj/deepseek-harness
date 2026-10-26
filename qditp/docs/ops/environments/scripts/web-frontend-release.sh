#!/bin/bash
# ============================================================================
# web 前端（CloudOS 应用包 web-frontend）一键发版
#
# 流程：本地出 zip → 登录 CloudOS → 上传 web 包（新增版本）→ 升级应用到该版本 → 核验/回滚
#
# 复用 lib/cloudos-* 的鉴权 / HTTP 通道（与 hongdao-deploy-route-sentinel.sh 同款），
# 端点 2026-10-26 实测：
#   登录  POST /api/sys/oapi/v1/double_factor/login（RSA 密码，lib/cloudos-auth.sh）
#   上传  POST /api/cloud/deploy/v1.0/packages        （multipart，pkgType=web，不是 jar）
#   升级  PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config
#         body 由「GET 应用详情的 metadata」读现状后只改 version_id/version（其余照抄）
#
# 生产写（上传 + 升级）MUST 带 --yes；缺省是 dry-run（本地出 zip + 打印将发的请求）。
# 回滚点＝升级前线上版本；出问题重跑一个更旧版本即可（PATCH 同端点、version 指回旧版）。
# ============================================================================

set -uo pipefail

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 仓库根：docs/ops/environments/scripts 上 4 级
REPO_ROOT="$(cd "${ITP_OPS_DIR}/../../../.." && pwd)"
WEB_DIR="${REPO_ROOT}/web"

source "${ITP_OPS_DIR}/lib/core.sh"
source "${ITP_OPS_DIR}/lib/env.sh"
source "${ITP_OPS_DIR}/lib/cloudos-auth.sh"
source "${ITP_OPS_DIR}/lib/cloudos-http.sh"
source "${ITP_OPS_DIR}/lib/cloudos-pkg.sh"

# ------------------------------------------------------------- 默认值 / 可覆盖
APP_NAME="${WEB_APP_NAME:-web-frontend}"   # 应用名
PKG_NAME="${WEB_PKG_NAME:-web-frontend}"   # 软件中心里的应用包名（同源）
PKG_TYPE="${WEB_PKG_TYPE:-web}"           # 上传 pkgType：web 包（不是 jar）
CLOUDOS_ENV="${CLOUDOS_ENV:-hongdao}"

APP_UUID="${WEB_APP_UUID:-}"
OPT_YES=0; SKIP_BUILD=0; SKIP_UPGRADE=0; VERSION=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --yes) OPT_YES=1 ;;
        --skip-build) SKIP_BUILD=1 ;;
        --skip-upgrade) SKIP_UPGRADE=1 ;;
        --app) shift; APP_NAME="${1:?--app 缺值}" ;;
        --pkg) shift; PKG_NAME="${1:?--pkg 缺值}" ;;
        --app-uuid) shift; APP_UUID="${1:?--app-uuid 缺值}" ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        -*) die "未知选项 $1（--yes / --skip-build / --skip-upgrade / --app / --pkg / --app-uuid）" ;;
        *) [[ -z "$VERSION" ]] && VERSION="$1" || die "多余参数：$1" ;;
    esac
    shift
done

[[ -n "$VERSION" ]] || { sed -n '2,15p' "$0"; die "缺少目标版本。例：$0 1.2.43.2 [--yes]"; }

ZIP="${WEB_DIR}/web-frontend-${VERSION}.zip"
log()  { printf '   %s\n' "$*"; }
info() { printf '\n[%s] %s\n' "$(basename "$0")" "$*"; }

# 解析 应用名→uuid（现查；不唯一/未找到则 die）
resolve_app_uuid() {
    local name="$1" out code body
    out=$(cloudos_http GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200")
    code=$(printf '%s' "$out" | head -n1); body=$(printf '%s' "$out" | sed '1d')
    [[ "$code" == "200" ]] || die "取应用列表失败 HTTP ${code}"
    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin); target = sys.argv[1]
hits = [a for a in (d.get("data") or []) if a.get("name") == target]
if not hits:
    hits = [a for a in (d.get("data") or []) if target in (a.get("name") or "")]
if len(hits) != 1:
    sys.stderr.write("应用名不唯一/未找到：%s（%d 个）\n" % (target, len(hits))); sys.exit(1)
print(hits[0]["uuid"])
' "$name"
}

# 取某应用包某版本的 version_id（不存在则空、返回 1；调用方判 -n）
get_version_id() {
    pkg_find_version "$PKG_NAME" "$VERSION" 2>/dev/null | cut -f1
}

# 应用现状：打印 "version status ok/num"
app_state() {
    cloudos_http GET "/api/cloud/app-mgmt/v1.0/apps/$1" | sed -n 2p | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or {}
print(d.get("version"), d.get("status"), "%s/%s" % (d.get("instance_ok_num"), d.get("instance_num")))'
}

# 读现状组装 advanced-config body：stdout=JSON body；stderr 打一行 ROLLBACK_POINT=<升级前版本>
build_upgrade_body() {
    local app_uuid="$1" tgt_vid="$2" out code body
    out=$(cloudos_http GET "/api/cloud/app-mgmt/v1.0/apps/${app_uuid}")
    code=$(printf '%s' "$out" | head -n1); body=$(printf '%s' "$out" | sed '1d')
    [[ "$code" == "200" ]] || die "取应用详情失败 HTTP ${code}"
    printf '%s' "$body" | VERSION_ID="$tgt_vid" VERSION="$VERSION" python3 -c '
import sys, json, os
d = json.load(sys.stdin).get("data") or {}
meta = d.get("metadata") or {}
img = (meta.get("image_metadata") or {}).get("metadata") or {}
payload = {
    "version_id": os.environ["VERSION_ID"],
    "version":    os.environ["VERSION"],
    "strategy":   meta.get("strategy", "rolling"),
    "env":        meta.get("env") or {},
    "jvm_args":   meta.get("jvm_args") or "",
    "logrotateCron": meta.get("logrotateCron"),
    "tomcat_version": meta.get("tomcat_version"),
    "is_skywalking":  meta.get("is_skywalking", False),
    "enable_ssl":     meta.get("enable_ssl", False),
    "nginx_version":  meta.get("nginx_version"),
    "service_name":   meta.get("service_name"),
    "affinity":       img.get("affinity", {}),
    "pvcResource":    img.get("pvcResource", {}),
    "xjarVersionPaths": {},
    "resources":      img.get("resources", []),
}
sys.stdout.write(json.dumps(payload, ensure_ascii=False))
sys.stderr.write("ROLLBACK_POINT=%s\n" % d.get("version"))
'
}

# 轮询到目标版本 + 满员（最多 ~120s）
wait_app_version() {
    local app_uuid="$1" target="$2" i state cur st ok
    for i in $(seq 1 40); do
        state=$(app_state "$app_uuid")
        cur="${state%% *}"; st="${state#* }"; st="${st%% *}"; ok="${state##* }"
        if [[ "$cur" == "$target" && "$st" == "OK" ]]; then
            log "✓ ${APP_NAME} 已到 ${cur}（status=${st}，实例 ${ok}，第 ${i} 次探测）"
            return 0
        fi
        log "… 当前 ${cur:-?} / ${st:-?} / ${ok:-?}（第 ${i} 次探测）"
        sleep 3
    done
    log "⚠ 120s 未达目标（${cur}/${st}）——MUST 登平台/查 kubectl 核实"
    return 1
}

# ============================================================================
info "== web 前端发版 ${VERSION}（env=${CLOUDOS_ENV} / app=${APP_NAME} / pkg=${PKG_NAME}:${PKG_TYPE}）=="
[[ "$OPT_YES" == "1" ]] || info "dry-run（未带 --yes）：本地出 zip + 打印将发的请求，不做任何生产写"

# ---- 0. 环境 profile（设 CLOUDOS_HOST / 凭据 / base url）
cloudos_env_init "$CLOUDOS_ENV" || exit 1

# ---- 1. 本地出 zip
if [[ "$SKIP_BUILD" == "1" ]]; then
    if [[ -f "$ZIP" ]]; then
        info "1/4 复用已构建 ${ZIP}（$(du -h "$ZIP" | cut -f1)）"
    else
        info "1/4 --skip-build 且 ${ZIP} 不存在：该版本若已在软件中心则跳过上传、不受影响；否则上传步会报错"
    fi
else
    info "1/4 本地构建（写 package.json version=${VERSION} + vite build + zip）"
    python3 - "$WEB_DIR/package.json" "$VERSION" <<'PY'
import sys, json
p, v = sys.argv[1], sys.argv[2]
d = json.load(open(p))
if d.get("version") != v:
    d["version"] = v
    json.dump(d, open(p, "w"), indent=2, ensure_ascii=False)
    open(p, "a").write("\n")
    print("   package.json version →", v)
else:
    print("   package.json version 已是", v)
PY
    ( cd "$WEB_DIR" && npm run build:zip >/tmp/web_frontend_build.log 2>&1 ) \
        || { tail -30 /tmp/web_frontend_build.log; die "前端构建失败（日志 /tmp/web_frontend_build.log）"; }
    [[ -f "$ZIP" ]] || die "构建未产出 $ZIP"
    if unzip -l "$ZIP" 2>/dev/null | awk '{print $4}' | grep -qx 'index.html'; then
        log "✓ zip 根级 index.html 校验通过（$(du -h "$ZIP" | cut -f1)）"
    else
        die "zip 内无根级 index.html（build:zip 产物异常，MUST 人工核对）"
    fi
fi

# ---- 2. 登录
info "2/4 登录 CloudOS（${CLOUDOS_HOST:-?} / ${CLOUDOS_USERNAME:-?}）"
cloudos_get_token >/dev/null || die "登录失败（RSA 公钥 / 账号 / 网络；先跑 itp-ops.sh cloudos auth-debug）"
log "✓ 登录成功（token 已缓存）"

# ---- 3. 上传 web 包（新增版本）
info "3/4 上传 web 包 ${PKG_NAME}:${VERSION}（pkgType=${PKG_TYPE}）"
if [[ -n "$(get_version_id 2>/dev/null)" ]]; then
    log "该版本已存在（vid=$(get_version_id)），跳过上传"
else
    if [[ "$OPT_YES" != "1" ]]; then
        log "dry-run：将 POST ${PKG_API_PREFIX}/packages（本地上传 ${ZIP##*/}）"
    else
        [[ -f "$ZIP" ]] || die "需要上传 ${PKG_NAME}:${VERSION} 但 zip 不存在：${ZIP}（去掉 --skip-build 先构建，或确认该版本已在软件中心）"
        vid=$(pkg_gen_id)
        up=$(cloudos_upload "${PKG_API_PREFIX}/packages" \
            -F "totalChunks=1" -F "chunkNumber=1" -F "id=${vid}" \
            -F "pkgType=${PKG_TYPE}" -F "name=${PKG_NAME}" -F "version=${VERSION}" \
            -F "visible=false" -F "label=${PKG_NAME}" \
            -F "file=@${ZIP};filename=web-frontend-${VERSION}.zip")
        upcode=$(printf '%s' "$up" | head -n1); upbody=$(printf '%s' "$up" | sed '1d')
        [[ "$upcode" == "200" ]] || die "上传失败 HTTP ${upcode}：${upbody:0:400}"
        printf '%s' "$upbody" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("上传业务失败：%s\n" % d.get("msg")); sys.exit(1)
' || die "上传业务失败：${upbody:0:300}"
        log "✓ 已上传（vid=${vid}）"
        [[ -n "$(get_version_id 2>/dev/null)" ]] \
            && log "✓ 回查确认 ${PKG_NAME}:${VERSION} 已落盘" \
            || log "⚠ 回查未命中（平台异步落库），稍后在软件中心复核"
    fi
fi

# ---- 4. 升级应用到该版本
if [[ "$SKIP_UPGRADE" == "1" ]]; then
    info "4/4 跳过升级（--skip-upgrade）"; exit 0
fi

if [[ -z "$APP_UUID" ]]; then
    APP_UUID=$(resolve_app_uuid "$APP_NAME")
    log "应用 ${APP_NAME} uuid=${APP_UUID}"
fi

info "4/4 升级 ${APP_NAME} → ${VERSION}"
if [[ "$OPT_YES" != "1" ]]; then
    tgt_vid="$(get_version_id 2>/dev/null || echo "<先上传后填>")"
    log "dry-run：将 PATCH /api/cloud/app-mgmt/v1.0/apps/${APP_UUID}/advanced-config"
    log "   version_id=${tgt_vid} version=${VERSION} strategy=rolling（body=应用详情 metadata 读现状改写）"
    log "   确认无误后：$0 ${VERSION} --yes   （上传/升级前可加 --skip-build 复用已出 zip）"
    exit 0
fi

tgt_vid=$(get_version_id)
[[ -n "$tgt_vid" ]] || die "上传后仍查不到 ${PKG_NAME}:${VERSION} 的 version_id"
body_json=$(build_upgrade_body "$APP_UUID" "$tgt_vid" 2>/tmp/web_frontend_rb.txt) || die "组装升级 body 失败"
rollback_note="$(grep -oE 'ROLLBACK_POINT=.+' /tmp/web_frontend_rb.txt | head -1 || true)"

patchout=$(cloudos_http PATCH "/api/cloud/app-mgmt/v1.0/apps/${APP_UUID}/advanced-config" "$body_json")
patchcode=$(printf '%s' "$patchout" | head -n1); patchbody=$(printf '%s' "$patchout" | sed '1d')
[[ "$patchcode" == "200" ]] || die "升级 PATCH 失败 HTTP ${patchcode}：${patchbody:0:400}"
printf '%s' "$patchbody" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("升级业务失败：%s\n" % d.get("msg")); sys.exit(1)
' || die "升级业务失败：${patchbody:0:300}"
log "✓ 升级请求已下发（${rollback_note:-未取到回滚点}）"

wait_app_version "$APP_UUID" "$VERSION" || true

info "完成：${APP_NAME} @ ${VERSION}（核验 / 回滚点见上；回滚＝$0 <旧版本> --yes）"
