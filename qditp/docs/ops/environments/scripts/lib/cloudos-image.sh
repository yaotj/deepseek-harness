#!/bin/bash
# ============================================================================
# CloudOS 镜像仓库 API（§容器云）—— 只封装本仓用得到的那几个端点
#
# 【前缀与基址（易错点）】
#   文档 §容器云 原文：「镜像服务api前缀：/api/cloud/hcr/v1.0」，基址写的是
#   `http://{平台IP}:11000`。但红岛实测 `/api/cloud/app-mgmt/v1.0` 挂在
#   `https://{vip}`(443) —— 说明各服务挂哪个端口不统一。
#   故本文件把基址独立成 IMG_BASE_URL：默认**惰性**跟随 CLOUDOS_BASE_URL
#   （CLOUDOS_BASE_URL 由 cloudos_env_init 在入口里后置设置，source 时还取不到，
#   所以 MUST 在调用时求值，不能写在顶层）；探测到别的基址时用环境变量覆盖：
#     IMG_BASE_URL=http://172.120.209.151:11000 itp-ops.sh cloudos image probe
#
# 【端点索引（仅本仓需要的）】
#   GET  /types                                  镜像类型（构建时 imgType 取值）
#   GET  /images/versions?harborProjectName=     镜像列表（确认 tag 占用、取回滚点）
#   GET  /image/versions?name=&type=             单镜像详情（含 digest）
#   POST /images/build                           镜像构建（dockerfile + fileFlag）
#   POST /images/checkImgExisted                 镜像是否已存在
#   POST /images/load                            上传镜像 tar（本仓不用：ARM 本机代价太大）
#
# 【注意】本章接口成功码是 `img.0` 而非空串，统一响应格式与 app-mgmt 不同
#   （{status,code,msg,data,httpCode}），所以判定只看 status 与 http_code。
#
# 依赖：cloudos-auth.sh、cloudos-http.sh
# ============================================================================

IMG_API_PREFIX="${IMG_API_PREFIX:-/api/cloud/hcr/v1.0}"

# 镜像服务的请求统一走 IMG_BASE_URL（默认跟随 CLOUDOS_BASE_URL，惰性求值），登录仍走 CLOUDOS_BASE_URL
_img_http()  { CLOUDOS_TARGET_BASE="${IMG_BASE_URL:-$CLOUDOS_BASE_URL}" cloudos_http "$@"; }
_img_upload() { CLOUDOS_TARGET_BASE="${IMG_BASE_URL:-$CLOUDOS_BASE_URL}" cloudos_upload "$@"; }

# 打印镜像类型列表：`uuid<TAB>name`
image_types() {
    local out code body
    out=$(_img_http GET "${IMG_API_PREFIX}/types")
    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')
    _is_net_fail "$code" && { printf 'NETWORK_UNREACHABLE\n'; return 1; }
    [[ "$code" == "200" ]] || { printf 'HTTP %s\n' "$code" >&2; return 1; }

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
rows = d.get("data")
if isinstance(rows, dict): rows = [rows]
for r in rows or []:
    if isinstance(r, dict):
        print("%s\t%s" % (r.get("uuid") or "", r.get("name") or ""))
'
}

# image_versions <harbor_project> [private|public]
#   打印：`imageName<TAB>tagCount<TAB>totalSize`
image_versions() {
    local project="${1:-itp}" type="${2:-private}"
    local out code body
    out=$(_img_http GET "${IMG_API_PREFIX}/images/versions?page=1&size=50&type=${type}&harborProjectName=${project}")
    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')
    _is_net_fail "$code" && { printf 'NETWORK_UNREACHABLE\n'; return 1; }
    [[ "$code" == "200" ]] || { printf 'HTTP %s\n' "$code" >&2; return 1; }

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
data = d.get("data") or {}
rows = data.get("data") if isinstance(data, dict) else data
for r in rows or []:
    if isinstance(r, dict):
        print("%s\t%s\t%s" % (r.get("imageName"), r.get("tagCount"), r.get("totalSize")))
'
}

# image_detail <name> [harbor_project] [private|public]
#   打印：`imgName<TAB>versions<TAB>digest<TAB>imgSize`
image_detail() {
    local name="$1" project="${2:-itp}" type="${3:-private}"
    local out code body
    out=$(_img_http GET "${IMG_API_PREFIX}/image/versions?name=${name}&type=${type}&harborProjectName=${project}")
    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')
    _is_net_fail "$code" && { printf 'NETWORK_UNREACHABLE\n'; return 1; }
    [[ "$code" == "200" ]] || { printf 'HTTP %s\n' "$code" >&2; return 1; }

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
for r in ((d.get("data") or {}).get("images") or []):
    print("%s\t%s\t%s\t%s" % (r.get("imgName"), r.get("versions"), r.get("digest"), r.get("imgSize")))
'
}

# image_check_existed <name> <version>
#   返回 0 表示已存在（同 tag 覆盖会违反「版本末位 +1」纪律，调用方据此拦下）
image_check_existed() {
    local name="$1" ver="$2" out code
    local payload
    payload=$(python3 -c 'import json,sys; print(json.dumps({"imgName":sys.argv[1],"version":sys.argv[2]}))' "$name" "$ver")
    out=$(_img_http POST "${IMG_API_PREFIX}/images/checkImgExisted" "$payload")
    code=$(printf '%s' "$out" | head -n1)
    _is_net_fail "$code" && return 2
    [[ "$code" == "200" ]] || return 2
    printf '%s' "$out" | sed '1d' | python3 -c '
import sys, json
d = json.load(sys.stdin)
print("EXISTED" if d.get("data") is True else "ABSENT")
' | grep -q EXISTED
}

# image_build <dockerfile_text> <imgName> <version> <imgType> <fileFlag> <project> [desc]
#   输出两行：http_code、body（body 里 data.build[]/data.push[] 是 docker 原始日志）
image_build() {
    local dockerfile="$1" name="$2" ver="$3" imgtype="$4" fileflag="$5" project="$6" desc="${7:-}"
    local payload
    payload=$(python3 -c '
import json, sys
print(json.dumps({
    "dockerfile": sys.argv[1], "imgName": sys.argv[2], "version": sys.argv[3],
    "imgType": sys.argv[4], "description": sys.argv[5], "type": "private",
    "fileFlag": sys.argv[6], "project": sys.argv[7],
}, ensure_ascii=False))' "$dockerfile" "$name" "$ver" "$imgtype" "$desc" "$fileflag" "$project")

    _img_http POST "${IMG_API_PREFIX}/images/build" "$payload"
}
