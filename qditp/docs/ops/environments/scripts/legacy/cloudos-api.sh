#!/bin/bash
# ============================================================================
# CloudOS 7.0 REST API 封装脚本（主入口）—— 测试环境 172.20.217.210
#
# ⚠️ 2026-10-23 更正：本脚本的「base64 明文」认证结论已被抓包证伪。
#    浏览器实际请求是 {"rsa":true,"password":"<RSA-1024 密文>"}，密文恒 172 字符。
#    本脚本 CLOUDOS_RSA_ENABLED=false 之所以「看起来能跑」，是因为它从未在
#    测试环境实跑通过过（本机到 172.20.217.210 长期不可达）。
#    → 红岛生产（172.120.209.151）已按 RSA 正确实现并实测通过，见同目录
#      `cloudos-hongdao.sh`；本文件待拿到测试环境 RSA 公钥后按同样方式修正。
#    → 另：本文件第 134 行的 `head -n -1` 在 macOS/BSD 上不支持负数，
#      本机跑必报 "head: -n < 0"，须换 `sed '$d'`（红岛脚本已改）。
# ============================================================================
#
# 【认证模型】账号密码 RSA 登录 → 拿 token → 调业务 API（完全不用 AK/SK 签名）
#   ①  POST /api/sys/oapi/v1/double_factor/login
#        Body: {username, rsa:true, password:<RSA 加密后的 base64 密文>, domain}
#        返回: {status, code, res: {token, ...}}
#   ②  业务 API 请求头：X-Auth-Token: <res.token>
#
# 【关于 RSA 密码】
#   本平台的密码传输方式是：明文 -> RSA 公钥加密 -> base64。
#   公钥固化在 Web 前端 JS 里（H3C CloudOS 标准做法）。本脚本的 CLOUDOS_RSA_PUBKEY
#   字段就是从那前端扒下来的。脚本用 openssl 做 RSA 加密 + base64，无需手工抓包。
#
#   ⚠ 公钥是平台侧配的，若将来平台更换，这里也要同步更新。
#   ⚠ 首次跑 auth_debug 若 401/403，先核对密码是否对、rsa 是否真的开。
#
# 参考：本地文档 UniCloudOS Plat E7501 API文档.md §6.16
#       https://172.20.217.210/api/sys/oapi/v1/double_factor/login
#
# 【两个高频排查点】（跑 auth_debug 后按返回码定位）：
#   - 登录 404  = URL 前缀不对（试试 /sys/oapi/... 不带 /api 前缀）
#   - 登录 401  = 密码错 / RSA 公钥过期 / 域名不对
#   - 业务 401  = token 路径取错 / token 过期（脚本每次自动重登，一般不触发）
#   - 连接超时  = 端口/网络（默认 443，可改 CLOUDOS_PORT=11000）
# ============================================================================

set -euo pipefail

# ============================================================================
# 配置（测试环境专用，全部硬编码，不依赖环境变量）
# ============================================================================
CLOUDOS_HOST="172.20.217.210"
CLOUDOS_PORT=""   # 空 = 走 443；填 11000 = 走 PaaS 默认端口（按 auth_debug 结果调）
CLOUDOS_USERNAME="admin"
CLOUDOS_PASSWORD_PLAIN="Qdmlc123!@#"   # 明文密码，脚本按 CLOUDOS_RSA_ENABLED 决定 base64/RSA 加密后发
CLOUDOS_RSA_ENABLED="false"            # 本环境实测：实际走 base64 模式（rsa 参数虽写 true，但后端不校验）
CLOUDOS_RSA_FIELD_IN_PAYLOAD="true"    # 独立控制 payload 里 "rsa" 字段的值（默认匹配前端 JS 的 true）
CLOUDOS_DOMAIN="default"               # 本地用户填 default；LDAP 用户填 ldap 域
CLOUDOS_INSECURE="true"                # 测试环境自签名证书，curl 加 -k 跳过校验；生产记得改 false

# RSA 公钥（PEM 格式，多行字符串保留换行）
# 来源：CloudOS Web 前端登录 JS 里固化的公钥。
# 抓取方法：
#   1) 浏览器登录 CloudOS Web 控制台，F12 打开开发者工具
#   2) Network 标签 → 点一次登录 → 找 double_factor/login 请求
#   3) 前端 JS 里搜 "BEGIN PUBLIC KEY" 或 "publicKey" 或 "rsaPublicKey" 关键字
#   4) 把整段 PEM 粘贴到下面（包含 BEGIN/END 行）
# 若留空且 CLOUDOS_RSA_ENABLED=true，脚本会拒绝运行并提示（避免拿错公钥静默失败）。
CLOUDOS_RSA_PUBKEY="-----BEGIN PUBLIC KEY-----
<这里粘贴你的 PEM 公钥>
-----END PUBLIC KEY-----"

# 业务 API base：填了端口走 https://host:port，没填走 https://host
if [[ -n "$CLOUDOS_PORT" ]]; then
    CLOUDOS_BASE_URL="https://${CLOUDOS_HOST}:${CLOUDOS_PORT}"
else
    CLOUDOS_BASE_URL="https://${CLOUDOS_HOST}"
fi

# ============================================================================
# 启动自检
# ============================================================================
if [[ "$CLOUDOS_RSA_ENABLED" == "true" && -z "${CLOUDOS_RSA_PUBKEY// }" ]] || \
   [[ "$CLOUDOS_RSA_ENABLED" == "true" && "$CLOUDOS_RSA_PUBKEY" == *"这里粘贴"* ]]; then
    echo "错误：CLOUDOS_RSA_ENABLED=true 但 CLOUDOS_RSA_PUBKEY 是占位符。"
    echo "  要么填入真实 RSA 公钥（从 Web 前端 JS 抓取）"
    echo "  要么把 CLOUDOS_RSA_ENABLED 改成 false 走 base64 明文模式"
    exit 1
fi

# ============================================================================
# 认证：账号密码登录换 token（base64 明文 / RSA 加密 两种模式）
# ============================================================================

# base64 编码密码（兼容 GNU / macOS / BSD base64）
b64_pw() {
    printf '%s' "$1" | (base64 -w0 2>/dev/null || base64 -b0 2>/dev/null || base64 | tr -d '\n')
}

# RSA 加密密码：
#   1) 把 CLOUDOS_RSA_PUBKEY 写进临时 pem 文件（避免 <(...) 进程替换跨 shell 问题）
#   2) 明文 -> openssl pkeyutl -encrypt（PKCS#1 v1.5 填充） -> base64
# 这是复刻浏览器前端 JS 的加密行为：PKCS#1 v1.5 + base64 编码，H3C 后端 Java 侧
# 用对应私钥 decrypt 后拿到明文密码比对。
rsa_encrypt_password() {
    if [[ -z "$CLOUDOS_RSA_PUBKEY" ]]; then
        echo "错误：CLOUDOS_RSA_PUBKEY 未填写，无法 RSA 加密密码" >&2
        exit 1
    fi
    local pubfile
    pubfile=$(mktemp)
    printf '%s\n' "$CLOUDOS_RSA_PUBKEY" > "$pubfile"
    printf '%s' "$1" | openssl pkeyutl -encrypt -pubin -inkey "$pubfile" | base64
    rm -f "$pubfile"
}

# 计算 password 字段（按 CLOUDOS_RSA_ENABLED 决定 base64 还是 RSA）
# 同时输出 payload 里要带的 "rsa": 布尔值
compute_password_field() {
    if [[ "$CLOUDOS_RSA_ENABLED" == "true" ]]; then
        rsa_encrypt_password "$CLOUDOS_PASSWORD_PLAIN"
    else
        b64_pw "$CLOUDOS_PASSWORD_PLAIN"
    fi
}

# 登录并解析出 token（取 res.token）
# 用 python3 解 JSON，避免 jq 依赖；HTTP 非 200 时打印响应到 stderr 便于排查
fetch_auth_token() {
    local pw_field
    pw_field=$(compute_password_field)

    # payload 里 "rsa" 字段值 = CLOUDOS_RSA_FIELD_IN_PAYLOAD（独立配置，默认 true 匹配前端习惯）
    # 而 password 字段的实际编码方式由 CLOUDOS_RSA_ENABLED 决定。
    # 本环境实测：CLOUDOS_RSA_ENABLED=false（base64）+ rsa 字段=true，后端能正常处理。
    local login_body
    login_body=$(printf '{"username":"%s","rsa":%s,"password":"%s","domain":"%s"}' \
        "$CLOUDOS_USERNAME" "$CLOUDOS_RSA_FIELD_IN_PAYLOAD" "$pw_field" "$CLOUDOS_DOMAIN")

    local login_resp
    # -w 在末尾追加 HTTP 状态码行；-s 关闭进度条
    login_resp=$(curl -s -k -w "\n%{http_code}" \
        -H "Content-Type: application/json" \
        -X POST \
        -d "$login_body" \
        "${CLOUDOS_BASE_URL}/api/sys/oapi/v1/double_factor/login")

    # 拆出 http_code 和 body（body 在 -w 行之前，可能含换行）
    local http_code
    http_code=$(echo "$login_resp" | tail -n1)
    local body
    body=$(echo "$login_resp" | head -n -1)

    if [[ "$http_code" != "200" ]]; then
        echo "登录失败 HTTP ${http_code}，响应：${body}" >&2
        exit 1
    fi

    echo "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
# H3C 文档 6.16 成功判定：status=true 且 code="0"（code 为空串也算成功）
if d.get("status") is False or str(d.get("code","")) not in ("", "0"):
    sys.stderr.write("登录业务失败: " + json.dumps(d, ensure_ascii=False) + "\n")
    sys.exit(1)
print(d["res"]["token"])
'
}

# ============================================================================
# 业务 API 请求（统一入口）
# 用法: cloudos_request METHOD URI [BODY] [CONTENT_TYPE]
# 说明: URI 为绝对路径（含 /api/... 前缀）。每次调用自动重新登录取 token。
#       若 token 有效期长，可加缓存优化；当前版本优先简单正确。
# ============================================================================
cloudos_request() {
    local method="$1"
    local uri="$2"
    local body="${3:-}"
    local content_type="${4:-application/json}"

    local token
    token=$(fetch_auth_token)

    local curl_args=(-s -w "\n%{http_code}" -X "$method"
        -H "Content-Type: ${content_type}"
        -H "Accept: application/json"
        -H "X-Auth-Token: ${token}")

    if [[ -n "$body" && "$method" != "GET" && "$method" != "HEAD" ]]; then
        curl_args+=(-d "$body")
    fi

    if [[ "$CLOUDOS_INSECURE" == "true" ]]; then
        curl_args+=(-k)
    fi

    # -w "\n%{http_code}"：请求体后单独一行打印 HTTP 状态码，方便一眼看 200/401/404
    curl "${curl_args[@]}" "${CLOUDOS_BASE_URL}${uri}"
}

# ============================================================================
# 应用仓库 API（/api/cloud/deploy/v1.0/*）
# 说明：应用包 = jar 制品 + 版本。list/get 是 GET，upload 是 multipart 单独处理。
# ============================================================================

# 查询应用包列表
# GET /api/cloud/deploy/v1.0/packages?page=1&size=20
app_repo_list() {
    local page="${1:-1}"
    local size="${2:-20}"
    cloudos_request GET "/api/cloud/deploy/v1.0/packages?page=${page}&size=${size}"
}

# 查询应用包详情
# GET /api/cloud/deploy/v1.0/packages/{id}
app_repo_get() {
    local id="$1"
    cloudos_request GET "/api/cloud/deploy/v1.0/packages/${id}"
}

# 上传应用包（本地文件）
# POST /api/cloud/deploy/v1.0/packages（multipart/form-data，不走统一 cloudos_request）
app_repo_upload() {
    local file_path="$1"
    local pkg_name="${2:-$(basename "$file_path")}"
    local version="${3:-v1}"
    local uri="/api/cloud/deploy/v1.0/packages"

    if [[ ! -f "$file_path" ]]; then
        echo "错误：文件不存在 ${file_path}"
        return 1
    fi

    local token
    token=$(fetch_auth_token)

    # 分片上传：单文件 totalChunks=1；大文件需拆片并把 totalChunks 改大
    curl -s -k -w "\n%{http_code}" -X POST \
        -H "Content-Type: multipart/form-data" \
        -H "X-Auth-Token: ${token}" \
        -F "totalChunks=1" \
        -F "chunkNumber=1" \
        -F "id=${pkg_name}-${version}" \
        -F "pkgType=jar" \
        -F "file=@${file_path}" \
        -F "name=${pkg_name}" \
        -F "version=${version}" \
        -F "visible=true" \
        "${CLOUDOS_BASE_URL}${uri}"
}

# ============================================================================
# 应用管理 API（/api/cloud/app-mgmt/v1.0/*）
# 说明：应用(app)=已部署实例；应用组(appgroup)=应用集合。
#       app_uuid 一般先 app_group_list / app_list 查出来再传给下面的命令。
# ============================================================================

# 查询应用组列表
# GET /api/cloud/app-mgmt/v1.0/appgroups
app_group_list() {
    cloudos_request GET "/api/cloud/app-mgmt/v1.0/appgroups"
}

# 查询应用列表
# GET /api/cloud/app-mgmt/v1.0/apps
app_list() {
    local appgroup_uuid="${1:-}"
    local url="/api/cloud/app-mgmt/v1.0/apps"
    if [[ -n "$appgroup_uuid" ]]; then
        url="${url}?appgroup_uuid=${appgroup_uuid}"
    fi
    cloudos_request GET "$url"
}

# 查询应用详情
# GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}
app_get() {
    local app_uuid="$1"
    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${app_uuid}"
}

# 启动/停止/重启应用
# PUT /api/cloud/app-mgmt/v1.0/apps/{app_uuid}
app_action() {
    local app_uuid="$1"
    local action="${2:-start}"  # start|stop|restart

    local body
    case "$action" in
        start)   body='{"action":"start"}' ;;
        stop)    body='{"action":"stop"}' ;;
        restart) body='{"action":"restart"}' ;;
        *)       echo "错误：不支持的操作 ${action}"; return 1 ;;
    esac

    cloudos_request PUT "/api/cloud/app-mgmt/v1.0/apps/${app_uuid}" "$body"
}

# 查询应用实例列表
# GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/instances
app_instances() {
    local app_uuid="$1"
    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${app_uuid}/instances"
}

# 查询应用日志
# GET /api/cloud/app-mgmt/v1.0/namespaces/{namespace}/pods/{pod_name}/log
app_logs() {
    local namespace="$1"
    local pod_name="$2"
    local tail_lines="${3:-100}"

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/namespaces/${namespace}/pods/${pod_name}/log?tailLines=${tail_lines}"
}

# ============================================================================
# 容器云 API（/api/cloud/container/v1.0/*）
# 说明：面向 K8s 原生的 Pod 管理；与上面「应用管理」的 app_uuid 体系不同，
#       这里是 pod_name + namespace，两套别混用。
# ============================================================================

# 查询 Pod 列表
# GET /api/cloud/container/v1.0/pods
pod_list() {
    local namespace="${1:-}"
    local url="/api/cloud/container/v1.0/pods"
    if [[ -n "$namespace" ]]; then
        url="${url}?namespace=${namespace}"
    fi
    cloudos_request GET "$url"
}

# 查询 Pod 详情
# GET /api/cloud/container/v1.0/pods/{pod_name}
pod_get() {
    local pod_name="$1"
    cloudos_request GET "/api/cloud/container/v1.0/pods/${pod_name}"
}

# 删除 Pod
# DELETE /api/cloud/container/v1.0/pods/{pod_name}
pod_delete() {
    local pod_name="$1"
    cloudos_request DELETE "/api/cloud/container/v1.0/pods/${pod_name}"
}

# ============================================================================
# 服务治理 API（/api/cloud/svcgw/v1.0/*）
# ============================================================================

# 查询独享集群列表
# GET /api/cloud/svcgw/v1.0/kaas_clusters
svc_cluster_list() {
    cloudos_request GET "/api/cloud/svcgw/v1.0/kaas_clusters"
}

# 查询集群工作节点
# GET /api/cloud/svcgw/v1.0/kaas_clusters/{uuid}（uuid 来自上一条 svc_cluster_list 的返回）
svc_cluster_nodes() {
    local uuid="$1"
    cloudos_request GET "/api/cloud/svcgw/v1.0/kaas_clusters/${uuid}"
}

# ============================================================================
# 认证调试命令
# ============================================================================

# 单独打印登录请求 + 解析出的 token，排查登录问题时首选
auth_debug() {
    local pw_field
    pw_field=$(compute_password_field)

    local login_body
    login_body=$(printf '{"username":"%s","rsa":%s,"password":"%s","domain":"%s"}' \
        "$CLOUDOS_USERNAME" "$CLOUDOS_RSA_FIELD_IN_PAYLOAD" "$pw_field" "$CLOUDOS_DOMAIN")

    local login_url="${CLOUDOS_BASE_URL}/api/sys/oapi/v1/double_factor/login"

    echo "===== 登录请求 ====="
    echo "URL:   ${login_url}"
    echo "rsa字段值: ${CLOUDOS_RSA_FIELD_IN_PAYLOAD}（payload 里写的值）"
    echo "password编码: ${CLOUDOS_RSA_ENABLED:-false}（true=RSA加密 / false=base64）"
    echo "Body:  ${login_body}"
    echo ""
    echo "----- 复制下面的 curl 直接跑 -----"
    echo "curl -s -k -X POST \\"
    echo "  -H \"Content-Type: application/json\" \\"
    echo "  -d '${login_body}' \\"
    echo "  \"${login_url}\""
    echo ""

    echo "===== 调用结果（自动解析 res.token） ====="
    local login_resp
    login_resp=$(curl -s -k -w "\n%{http_code}" \
        -H "Content-Type: application/json" \
        -X POST \
        -d "$login_body" \
        "$login_url")

    local http_code
    http_code=$(echo "$login_resp" | tail -n1)
    local body
    body=$(echo "$login_resp" | head -n -1)

    echo "HTTP: ${http_code}"
    echo "Body: ${body}"
    echo ""

    if [[ "$http_code" == "200" ]]; then
        local token
        token=$(echo "$body" | python3 -c 'import sys,json; print(json.load(sys.stdin)["res"]["token"])' 2>/dev/null || echo "(解析 token 失败)")
        echo "解析出的 token: ${token}"
        echo ""
        echo "----- 下一步：拿 token 调业务 API（直接复制跑） -----"
        echo "curl -s -k -X GET \"${CLOUDOS_BASE_URL}/api/cloud/app-mgmt/v1.0/apps\" \\"
        echo "  -H \"Content-Type: application/json\" \\"
        echo "  -H \"X-Auth-Token: ${token}\""
    fi

    echo ""
    echo "===== 返回码对照（对号入座） ====="
    echo "  HTTP 200 + code=0    => 登录成功，token 已取到，可直接调业务 API"
    echo "  HTTP 401/403         => 密码错 / RSA 公钥过期 / 域名不对（若 base64 模式失败，"
    echo "                          把 CLOUDOS_RSA_ENABLED 改 true 并填 CLOUDOS_RSA_PUBKEY 再试）"
    echo "  HTTP 404             => URL 前缀不对，试把 CLOUDOS_BASE_URL 的 /api 前缀去掉"
    echo "  连接超时/502         => 端口不通：改 CLOUDOS_PORT=11000 或换能通内网的机器"
}

# ============================================================================
# 批量操作工具
# ============================================================================

# 列出所有支持的 API（用于帮助和 tab 补全）
api_list() {
    cat << 'EOF'
认证:
  auth_debug - 登录并打印 token + 可直接复制的业务 curl（排查首选）

应用仓库:
  app_repo_list   - GET  /api/cloud/deploy/v1.0/packages
  app_repo_get    - GET  /api/cloud/deploy/v1.0/packages/{id}
  app_repo_upload - POST /api/cloud/deploy/v1.0/packages

应用管理:
  app_group_list  - GET  /api/cloud/app-mgmt/v1.0/appgroups
  app_list        - GET  /api/cloud/app-mgmt/v1.0/apps
  app_get         - GET  /api/cloud/app-mgmt/v1.0/apps/{app_uuid}
  app_action      - PUT  /api/cloud/app-mgmt/v1.0/apps/{app_uuid}
  app_instances   - GET  /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/instances
  app_logs        - GET  /api/cloud/app-mgmt/v1.0/namespaces/{ns}/pods/{pod}/log

容器云:
  pod_list   - GET  /api/cloud/container/v1.0/pods
  pod_get    - GET  /api/cloud/container/v1.0/pods/{pod_name}
  pod_delete - DELETE /api/cloud/container/v1.0/pods/{pod_name}

服务治理:
  svc_cluster_list - GET  /api/cloud/svcgw/v1.0/kaas_clusters
  svc_cluster_nodes - GET  /api/cloud/svcgw/v1.0/kaas_clusters/{uuid}
EOF
}

# ============================================================================
# 主入口
# ============================================================================

usage() {
    echo "CloudOS 7.0 REST API 封装脚本（账号密码 RSA 登录版）"
    echo ""
    echo "认证: 账号密码 RSA 登录 -> 拿 res.token -> 业务 API 带 X-Auth-Token"
    echo "配置见脚本顶部硬编码区（CLOUDOS_USERNAME/PASSWORD/DOMAIN/RSA_PUBKEY/PORT）"
    echo ""
    echo "用法: $0 <命令> [参数...]"
    echo ""
    echo "命令:"
    api_list
    echo ""
    echo "示例:"
    echo "  $0 auth_debug          # 先排查登录是否通、token 是否能取到"
    echo "  $0 app-list"
    echo "  $0 app-get <app_uuid>"
    echo "  $0 app-action <app_uuid> restart"
}

main() {
    local cmd="${1:-}"
    shift || true

    # 支持下划线和横线两种写法（互相转换）
    local normalized_cmd
    normalized_cmd=$(echo "$cmd" | tr '_' '-')

    case "$normalized_cmd" in
        --help|-h|help)
            usage
            ;;
        api-list|"")
            api_list
            ;;
        auth-debug|token-debug)
            auth_debug
            ;;
        app-repo-list)
            app_repo_list "$@"
            ;;
        app-repo-get)
            app_repo_get "$@"
            ;;
        app-repo-upload)
            app_repo_upload "$@"
            ;;
        app-group-list)
            app_group_list "$@"
            ;;
        app-list)
            app_list "$@"
            ;;
        app-get)
            app_get "$@"
            ;;
        app-action)
            app_action "$@"
            ;;
        app-instances)
            app_instances "$@"
            ;;
        app-logs)
            app_logs "$@"
            ;;
        pod-list)
            pod_list "$@"
            ;;
        pod-get)
            pod_get "$@"
            ;;
        pod-delete)
            pod_delete "$@"
            ;;
        svc-cluster-list)
            svc_cluster_list "$@"
            ;;
        svc-cluster-nodes)
            svc_cluster_nodes "$@"
            ;;
        *)
            echo "错误：未知命令 ${cmd}"
            usage
            exit 1
            ;;
    esac
}

main "$@"
