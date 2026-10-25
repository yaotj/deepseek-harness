#!/bin/bash
# ============================================================================
# CloudOS 应用仓库 API（软件中心）—— jar 上传与版本回查
#
# 【为什么要上传 jar】
#   镜像构建接口 /images/build 只接受 dockerfile 字符串 + fileFlag，没有 jarPath
#   （jarPath 在持续交付的 BUILD_IMAGE 任务里）。所以「本地 jar → 镜像」要么把 jar
#   先落到平台的文件存储、由 fileFlag 引用，要么走持续交付。本文件负责前者。
#
# 【两处文档空白（联调时 MUST 实测，勿当结论）】
#   1. `id`（应用包版本 ID）文档标注必填却「未说明由谁生成」。全仓口径是**客户端
#      预生成**：证据是 /images/load 把 fileFlag 放请求头必填、而返回体不含任何 ID，
#      服务端没机会下发。故这里由调用方传入。
#   2. 上传接口**不返回任何 ID**（data 只有 true），版本 ID 只能靠 GET 列表按
#      name+version 回查 —— 见 pkg_find_version。
#
# 【端点】
#   POST /api/cloud/deploy/v1.0/packages           上传应用包（multipart 分片）
#   GET  /api/cloud/deploy/v1.0/packages?name=     回查包与版本 ID
#   GET  /api/cloud/deploy/v1.0/package-files/{id} 下载应用包（octet-stream）
#   DELETE /api/cloud/deploy/v1.0/packages/{id}/batchDel  删除版本
#
# 依赖：cloudos-auth.sh、cloudos-http.sh
# ============================================================================

PKG_API_PREFIX="${PKG_API_PREFIX:-/api/cloud/deploy/v1.0}"

# pkg_upload <file> <name> <version> <id> [filename] [--public]
#   单分片上传（totalChunks=1）。filename 默认取实际文件名；传 app.jar 可对齐
#   共享 Dockerfile 里的 `COPY app.jar`。
pkg_upload() {
    local file="$1" name="$2" ver="$3" id="$4"
    local filename="${5:-$(basename "$1")}" visible="false"
    [[ "${6:-}" == "--public" ]] && visible="true"

    local out code body
    out=$(cloudos_upload "${PKG_API_PREFIX}/packages" \
        -F "totalChunks=1" -F "chunkNumber=1" \
        -F "id=${id}" -F "pkgType=jar" \
        -F "name=${name}" -F "version=${ver}" \
        -F "visible=${visible}" -F "label=${name}" \
        -F "file=@${file};filename=${filename}")

    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')
    _is_net_fail "$code" && { printf 'NETWORK_UNREACHABLE\n' >&2; return 1; }
    [[ "$code" == "200" ]] || { printf '上传 HTTP %s：%s\n' "$code" "${body:0:400}" >&2; return 1; }

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("上传失败：code=%s msg=%s\n" % (d.get("code"), d.get("msg"))); sys.exit(1)
' || return 1
}

# pkg_find_version <pkg_name> <version>
#   打印该版本的 version_id（短 ID，形如 cGjEhXIdOe），同时输出落盘文件名与大小便于自检。
#   找不到返回 1。
pkg_find_version() {
    local name="$1" ver="$2" out code body
    out=$(cloudos_http GET "${PKG_API_PREFIX}/packages?page=1&size=20&name=${name}")
    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')
    _is_net_fail "$code" && { printf 'NETWORK_UNREACHABLE\n' >&2; return 1; }
    [[ "$code" == "200" ]] || { printf 'HTTP %s\n' "$code" >&2; return 1; }

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
target = sys.argv[1]
for pkg in (d.get("data") or []):
    for v in (pkg.get("versions") or []):
        if v.get("version") == target:
            print("%s\t%s\t%s" % (v.get("id"), v.get("file_name"), v.get("file_size")))
            sys.exit(0)
sys.exit(1)
' "$ver"
}

# pkg_delete_version <pkg_uuid> <version_id> [--force]
pkg_delete_version() {
    local uuid="$1" vid="$2" force="false"
    [[ "${3:-}" == "--force" ]] && force="true"
    local payload
    payload=$(python3 -c 'import json,sys; print(json.dumps([{"versionId":sys.argv[1],"forced":sys.argv[2]=="true"}]))' "$vid" "$force")
    local out code
    out=$(cloudos_http DELETE "${PKG_API_PREFIX}/packages/${uuid}/batchDel" "$payload")
    code=$(printf '%s' "$out" | head -n1)
    [[ "$code" == "200" ]] || { printf '删除 HTTP %s：%s\n' "$code" "$(printf '%s' "$out" | sed '1d' | head -c 300)" >&2; return 1; }
}

# 生成 10 位短 ID（与平台应用包版本 ID 同格式）
pkg_gen_id() {
    python3 -c '
import random, string
print("".join(random.choice(string.ascii_letters + string.digits) for _ in range(10)))'
}
