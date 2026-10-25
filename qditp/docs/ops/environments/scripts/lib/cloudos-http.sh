#!/bin/bash
# ============================================================================
# CloudOS HTTP 通道 —— 统一三件事：base url / 鉴权头 / 网络失败识别
#
# 【三个通道，按响应体形态分，不要混用】
#   cloudos_http     JSON 通道。返回两行：`<http_code>\n<body>`，不 die
#   cloudos_upload   multipart 通道（文件分片）。返回同上，不 die
#   cloudos_raw     原始通道（日志/文件流）。body 不加工，NEVER 过 json.dumps
#
# 【为什么底层不 die】
#   调用方常需要按状态码分流（404 说明路由不在 / 401 说明缺凭据 / 400 说明参数错），
#   die 在这里会把「探测」变成「中断」。需要 die 语义的用 cloudos_json。
#
# 【网络失败 MUST 显式识别】
#   curl 超时/代理 502 时不写 stdout，http_code 为空或 000。若不拦，调用方拿空
#   stdin 去 json.load 会抛 traceback，把人误导向「接口坏了」。这里统一返回
#   code=NETWORK_UNREACHABLE 的合法 JSON。
#
# 【目标基址可临时覆盖】
#   默认打 CLOUDOS_BASE_URL（Web 网关，已验证 443）。镜像服务在另一前缀甚至另一端口
#   （文档写 http://{ip}:11000 + /api/cloud/hcr/v1.0），调用方用变量前缀临时覆盖：
#     CLOUDOS_TARGET_BASE="$IMG_BASE_URL" cloudos_http GET "/api/cloud/hcr/v1.0/types"
#   登录始终走 CLOUDOS_BASE_URL，不受该覆盖影响。
#
# 依赖：cloudos-auth.sh（cloudos_get_token、CLOUDOS_BASE_URL、CLOUDOS_TIMEOUT）
# ============================================================================

CLOUDOS_NET_ERR_JSON='{"status":false,"code":"NETWORK_UNREACHABLE","msg":"连不上目标平台","data":null,"total":0}'

_is_net_fail() { [[ -z "${1:-}" || "$1" == "000" ]]; }

# 拿 token，失败返回 1（不打印，输出形态交给调用方决定）。
# 不能在命令替换里 die —— 子 shell 的 exit 传不出来，父 shell 会拿空 token 继续。
_need_token() { cloudos_get_token "${1:-}"; }

# cloudos_http METHOD URI [JSON_BODY] [--fresh]
#   输出两行：http_code、body。不做 JSON 解析，不改 body。
cloudos_http() {
    local method="$1" uri="$2" body="${3:-}" fresh="${4:-}"
    local base="${CLOUDOS_TARGET_BASE:-$CLOUDOS_BASE_URL}"
    local token args resp http_code
    token=$(_need_token "$fresh") || { printf '000\n%s\n' "$CLOUDOS_NET_ERR_JSON"; return 1; }

    args=(-sSk --max-time "$CLOUDOS_TIMEOUT" -w "\n%{http_code}" -X "$method"
        -H "Content-Type: application/json" -H "Accept: application/json"
        -H "X-Auth-Token: ${token}")
    if [[ -n "$body" && "$method" != "GET" && "$method" != "HEAD" ]]; then
        args+=(-d "$body")
    fi

    resp=$(curl "${args[@]}" "${base}${uri}")
    http_code=$(printf '%s' "$resp" | tail -n1)
    printf '%s\n' "$http_code"
    printf '%s' "$resp" | sed '$d'
}

# cloudos_json METHOD URI [BODY] [--fresh]
#   成功（HTTP 200 且 status!=false）打印美化 JSON；失败打印原因并 exit 1。
#   语义与 cloudos-hongdao.sh 的 cloudos_request 一致，便于逐段替换。
cloudos_json() {
    local method="$1" uri="$2" body="${3:-}" fresh="${4:-}"
    local out http_code rbody
    out=$(cloudos_http "$method" "$uri" "$body" "$fresh")

    http_code=$(printf '%s' "$out" | head -n1)
    rbody=$(printf '%s' "$out" | sed '1d')

    if _is_net_fail "$http_code"; then
        printf '%s\n' "$CLOUDOS_NET_ERR_JSON"
        return 1
    fi

    # token 过期自动重登一次
    if [[ "$http_code" == "401" || "$http_code" == "403" ]] && [[ "$fresh" != "--fresh" ]]; then
        out=$(cloudos_http "$method" "$uri" "$body" --fresh)
        http_code=$(printf '%s' "$out" | head -n1)
        rbody=$(printf '%s' "$out" | sed '1d')
        if _is_net_fail "$http_code"; then
            printf '%s\n' "$CLOUDOS_NET_ERR_JSON"
            return 1
        fi
    fi

    if [[ "$http_code" != "200" ]]; then
        printf '错误：HTTP %s %s %s：%s\n' "$http_code" "$method" "$uri" "${rbody:0:500}" >&2
        return 1
    fi

    printf '%s' "$rbody" | python3 -c '
import sys, json
raw = sys.stdin.read()
try:
    d = json.loads(raw)
except Exception:
    print(raw); sys.exit(0)
if isinstance(d, dict) and d.get("status") is False:
    sys.stderr.write("业务失败：code=%s msg=%s\n" % (d.get("code"), d.get("msg")))
    print(json.dumps(d, ensure_ascii=False, indent=2)); sys.exit(1)
print(json.dumps(d, ensure_ascii=False, indent=2))
'
}

# cloudos_upload URI FORM_ARGS...
#   FORM_ARGS 为 curl -F 的成对参数，例如：
#     cloudos_upload "/api/cloud/deploy/v1.0/packages" \
#        -F "id=xxx" -F "pkgType=jar" -F "file=@a.jar;filename=app.jar"
#   输出两行：http_code、body。
cloudos_upload() {
    local uri="$1"; shift
    local base="${CLOUDOS_TARGET_BASE:-$CLOUDOS_BASE_URL}"
    local token resp http_code
    token=$(_need_token) || { printf '000\n%s\n' "$CLOUDOS_NET_ERR_JSON"; return 1; }

    resp=$(curl -sSk --max-time 300 -w "\n%{http_code}" \
        -X POST -H "X-Auth-Token: ${token}" "$@" "${base}${uri}")
    http_code=$(printf '%s' "$resp" | tail -n1)
    printf '%s\n' "$http_code"
    printf '%s' "$resp" | sed '$d'
}

# cloudos_raw URI
#   原样取 body（日志、octet-stream）。调用方自行处理。
cloudos_raw() {
    local uri="$1" token
    local base="${CLOUDOS_TARGET_BASE:-$CLOUDOS_BASE_URL}"
    token=$(_need_token) || return 1
    curl -sSk --max-time "$CLOUDOS_TIMEOUT" \
        -H "Accept: */*" -H "X-Auth-Token: ${token}" "${base}${uri}"
}

# 连通性预检：nc 指定端口。返回 0 = 至少一个通。
cloudos_check_ports() {
    local host="$1"; shift
    local ok=0 port
    for port in "$@"; do
        if nc -z -G 5 "$host" "$port" 2>/dev/null; then
            printf '   %s 通\n' "$port"; ((ok++))
        else
            printf '   %s 不通\n' "$port"
        fi
    done
    return $(( ok == 0 ? 1 : 0 ))
}
