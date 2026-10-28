#!/bin/bash
# ============================================================================
# itp-ops · cloudos 域 —— 新华三 CloudOS（E7501 PaaS）平台运维
#
# 【本文件是什么】
#   2026-10-25 重构：原 cloudos-hongdao.sh（939 行，红岛应用运维）+ image-build.sh
#   （329 行，本地 jar → 平台打镜像）+ cloudos-api.sh（546 行，测试环境 API 封装）
#   三合一为一个域，认证/HTTP 全部走 lib/cloudos-*。命令与输出逐条保留原样；
#   差异只有三处，都有理由：
#     1. --yes 改由入口全局解析（OPT_YES），不再占位参数 —— 命令行写法不变
#        （itp-ops.sh cloudos scale fep-app 2 --yes 与旧 cloudos-hongdao.sh 等价）
#     2. 测试环境命令从「自带一套 base64 明文认证」改为走共享 lib 的 RSA 认证
#        （旧结论 2026-10-23 已被抓包证伪，见 lib/cloudos-auth.sh 头注）
#     3. 测试环境原始 HTTP dump 输出改为美化 JSON（信息不变，可读性提升）
#
# 【环境】
#   --env hongdao（默认，红岛生产 172.120.209.151 / itpbadmin）
#   --env test   （测试 172.20.217.210 / admin；RSA 公钥未抓到，见 env_init 报错）
#   每个命令执行前都会打印当前环境横幅，NEVER 猜自己在打哪个环境。
#
# 【红岛环境事实】
#   命名空间 itpb；Service port = NodePort = 319xx；targetPort 统一 8080；
#   平台按 application.properties 生成的 env 保留测试默认值，不显式注入
#   SERVICE_*_URL 就会指向 itp 命名空间 → 跨模块 RPC 全失败（env 命令可核）。
#
# 依赖：lib/core.sh、lib/cloudos-auth.sh、lib/cloudos-http.sh、lib/cloudos-image.sh、lib/cloudos-pkg.sh
# ============================================================================

# ---------------------------------------------------------------- 域级默认值
HARBOR_PROJECT="${HARBOR_PROJECT:-itp}"
JAR_OVERRIDE="${JAR_OVERRIDE:-}"
IMG_TYPE_UUID="${IMG_TYPE_UUID:-}"
DOCKERFILE="${DOCKERFILE:-}"          # 空 = 仓库根 docker/Dockerfile，main 里兜底

# ---------------------------------------------------------------- 请求封装
# 与旧 cloudos-hongdao.sh 的 cloudos_request 同语义：成功打美化 JSON 到 stdout，
# 失败把原因写 stderr 并退出（绝不把半截 body 喂给下游 python —— 那会以
# JSONDecodeError traceback 的形式把人误导向「接口坏了」）。
cloudos_request() {
    local out rc
    out=$(cloudos_json "$@"); rc=$?
    if [[ $rc -ne 0 ]]; then
        [[ -n "$out" ]] && printf '%s\n' "$out" >&2
        die "CloudOS 请求失败（$*）"
    fi
    printf '%s\n' "$out"
}

# ---------------------------------------------------------------- 参数解析：uuid 或应用名 → uuid
resolve_app_uuid() {
    local key="$1"
    # 32 位 hex 视为 uuid 直接用
    if [[ "$key" =~ ^[0-9a-f]{32}$ ]]; then
        echo "$key"; return 0
    fi

    local uuid
    uuid=$(cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200" | python3 -c '
import sys, json
name = sys.argv[1]
d = json.load(sys.stdin)
hits = [a for a in (d.get("data") or []) if a.get("name") == name]
if not hits:
    hits = [a for a in (d.get("data") or []) if name in (a.get("name") or "")]
if len(hits) == 1:
    print(hits[0]["uuid"])
elif not hits:
    sys.stderr.write("未找到应用：%s\n" % name); sys.exit(1)
else:
    sys.stderr.write("应用名不唯一，请用 uuid：%s\n" % ", ".join(h["name"] for h in hits)); sys.exit(1)
' "$key") || die "解析应用失败：${key}"
    echo "$uuid"
}

# ============================================================================
# 业务命令（自 cloudos-hongdao.sh 逐条迁移，逻辑未改）
# ============================================================================

# 连通性自检：任何写操作（scale / spec / act）之前先跑这个。
# 红岛靠 USB 网卡 en9 接入，网卡一 inactive，所有请求都会以「502 / 超时」形式失败，
# 看着像平台故障，实际是本机链路断了 —— 这一步就是为了把两者分开。
health() {
    local bad=0

    echo "===== CloudOS 连通性自检（${CLOUDOS_HOST}）====="
    echo ""

    # 1) 本机网卡（en9 是红岛 USB 网卡；测试环境可能走别的网卡，不存在只提示不判错）
    if ifconfig en9 >/dev/null 2>&1; then
        if ifconfig en9 2>/dev/null | grep -q "status: active"; then
            local ip
            ip=$(ifconfig en9 2>/dev/null | awk '/inet /{print $2; exit}')
            echo "  [1/3] en9: active  (${ip:-无IP})"
        else
            echo "  [1/3] en9: **inactive** —— USB 网卡未插好/网线断，这就是根因"
            bad=1
        fi
    else
        echo "  [1/3] en9: 不存在（可能走别的网卡，继续测）"
    fi

    # 2) TCP 443
    if nc -z -G 5 "$CLOUDOS_HOST" 443 2>/dev/null; then
        echo "  [2/3] TCP ${CLOUDOS_HOST}:443 通"
    else
        echo "  [2/3] TCP ${CLOUDOS_HOST}:443 **不通**（172.120 段路由是否还指向可用接口？）"
        bad=1
    fi

    # 3) 登录 + 一次业务 GET
    if [[ "$bad" -eq 0 ]]; then
        local t0 t1 token
        t0=$(date +%s)
        if token=$(cloudos_get_token --fresh 2>/dev/null) && [[ -n "$token" ]]; then
            t1=$(date +%s)
            echo "  [3/3] 登录成功（$((t1 - t0))s，token ${#token} 字符）"
            t0=$(date +%s)
            if cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=1" >/dev/null 2>&1; then
                t1=$(date +%s)
                echo "        业务 API 正常（$((t1 - t0))s）"
            else
                echo "        业务 API **失败**（登录通了但 app-mgmt 无响应）"
                bad=1
            fi
        else
            echo "  [3/3] **登录失败** —— 账号/密码/RSA 公钥，先跑 auth-debug"
            bad=1
        fi
    else
        echo "  [3/3] 跳过（链路不通，先修网络）"
    fi

    echo ""
    if [[ "$bad" -eq 0 ]]; then
        echo "结论：可以操作"
        return 0
    fi
    echo "结论：**不能操作**，先按上面提示排障"
    return 1
}

# 登录诊断
auth_debug() {
    local enc
    enc=$(cloudos_rsa_encrypt "$CLOUDOS_PASSWORD")

    echo "===== 登录请求 ====="
    echo "URL:      ${CLOUDOS_BASE_URL}/api/sys/oapi/v1/double_factor/login"
    echo "账号:     ${CLOUDOS_USERNAME}  (domain=${CLOUDOS_DOMAIN})"
    echo "密码模式: RSA / PKCS#1 v1.5 / 1024-bit"
    echo "密文长度: ${#enc} 字符（128 字节；不是 172 说明公钥或填充不对）"
    cloudos_prepare_pubkey
    echo "公钥指纹: $(openssl pkey -pubin -in "$CLOUDOS_PUB_PEM_FILE" -outform DER 2>/dev/null \
        | openssl dgst -sha256 -r | cut -d' ' -f1)"
    echo ""

    local resp http_code rbody
    resp=$(curl -sSk --max-time "$CLOUDOS_TIMEOUT" -w "\n%{http_code}" \
        -H "Content-Type: application/json" -X POST \
        -d "$(printf '{"username":"%s","rsa":true,"password":"%s","domain":"%s"}' \
            "$CLOUDOS_USERNAME" "$enc" "$CLOUDOS_DOMAIN")" \
        "${CLOUDOS_BASE_URL}/api/sys/oapi/v1/double_factor/login")
    http_code=$(echo "$resp" | tail -n1)
    rbody=$(printf '%s' "$resp" | sed '$d')

    echo "HTTP: ${http_code}"
    printf '%s' "$rbody" | python3 -c '
import sys, json
d = json.load(sys.stdin)
print("status=%s code=%s msg=%s" % (d.get("status"), d.get("code"), d.get("msg")))
if d.get("status") is True:
    t = d["res"]["token"]
    print("token=" + t)
    print("token前缀=%s 长度=%d" % (t[:10], len(t)))
'
    echo ""
    echo "返回码对照："
    echo "  status=true                 => 通过，可用 apps / app <name> 继续"
    echo "  usrmgt.userOrPasswordError  => 账号或密码错（先确认 itpbadmin，再确认 RSA 加密）"
    echo "  svcmgt.internalServerError  => domain 传空导致，domain MUST 为 default"
}

# 应用列表
apps() {
    local status_filter="${1:-}"
    local uri="/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200"
    [[ -n "$status_filter" ]] && uri="${uri}&status=${status_filter}"

    cloudos_request GET "$uri" | python3 -c '
import sys, json
d = json.load(sys.stdin)
rows = d.get("data") or []
print("共 %d 个应用" % len(rows))
print("%-22s %-34s %-10s %-7s %-8s %s" % ("NAME", "UUID", "STATUS", "INST", "VERSION", "NAMESPACE"))
print("-" * 108)
for a in sorted(rows, key=lambda x: (x.get("name") or "")):
    inst = "%s/%s" % (a.get("instance_num"), a.get("instance_ok_num"))
    print("%-22s %-34s %-10s %-7s %-8s %s" % (
        (a.get("name") or "")[:22], a.get("uuid") or "",
        a.get("status") or "", inst, (a.get("version") or "")[:8], a.get("namespace") or ""))
'
}

# 应用详情（关键字段 + env）
app_detail() {
    local key="$1"
    local uuid
    uuid=$(resolve_app_uuid "$key")

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${uuid}" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or {}
meta = d.get("metadata") or {}
env = meta.get("env") or {}
print("name         :", d.get("name"))
print("uuid         :", d.get("uuid"))
print("namespace    :", d.get("namespace"))
print("status       :", d.get("status"))
print("version      :", d.get("version"))
print("vip          :", d.get("vip"))
print("service_name :", meta.get("service_name"))
print("cluster      :", d.get("cluster_name") or d.get("cluster_id"))
print("replicas     :", d.get("instance_num"), "ok=", d.get("instance_ok_num"))
print("created_at   :", d.get("created_at"))
print("description  :", d.get("description"))
print("")
if env:
    print("--- env（%d 项，按 key 排序）---" % len(env))
    for k in sorted(env):
        print("%s=%s" % (k, env[k]))
'
}

# 只打 env —— 排查跨模块 RPC 地址（SERVICE_*_URL 是否还指向 itp）首选
app_env() {
    local key="$1"
    local uuid
    uuid=$(resolve_app_uuid "$key")

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${uuid}" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or {}
env = (d.get("metadata") or {}).get("env") or {}
print("# %s / %s / ns=%s / v=%s" % (d.get("name"), d.get("uuid"), d.get("namespace"), d.get("version")))
for k in sorted(env):
    print("%s=%s" % (k, env[k]))
'
}

# 访问方式（NodePort 映射）
app_service() {
    local key="$1"
    local uuid
    uuid=$(resolve_app_uuid "$key")

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${uuid}/service" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or {}
print("vip:", d.get("vip"))
for s in d.get("services") or []:
    print("service: %s  type=%s" % (s.get("service_name"), s.get("type")))
    print("  %-18s %-8s %-16s %-12s %s" % ("PORT_NAME", "PROTO", "CONTAINER_PORT", "CLUSTER", "NODEPORT"))
    for p in s.get("port_map_list") or []:
        print("  %-18s %-8s %-16s %-12s %s" % (
            p.get("name"), p.get("protocol"), p.get("container_port"),
            p.get("cluster_port"), p.get("node_port")))
'
}

# 实例（pod）列表
app_pods() {
    local key="$1"
    local uuid
    uuid=$(resolve_app_uuid "$key")

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${uuid}/instances" | python3 -c '
import sys, json
rows = json.load(sys.stdin).get("data") or []
print("%-42s %-6s %-16s %-6s %s" % ("POD", "READY", "POD_IP", "RESTART", "CONTAINERS"))
print("-" * 110)
for p in rows:
    cs = ",".join(c.get("name") or "" for c in (p.get("containers") or []))
    print("%-42s %-6s %-16s %-6s %s" % (
        p.get("name"), p.get("ready"), p.get("podIP"),
        p.get("restartTime"), cs))
'
}

# 运行日志：默认取第一个 ready pod 的第一个容器
#   ⚠ 2026-10-23 红岛实测：接口恒返回 HTTP 200 + 空 body（换 pod、换容器 fep-app /
#     istio-proxy 均空）。日志真源在 VictoriaLogs（172.120.2.13:31920）或 kubectl logs
#     （itp-ops.sh log tail <svc>），本命令留着是为了将来平台侧修好后直接可用。
app_logs() {
    local key="$1"
    local lines="${2:-200}"
    local uuid ns pod container
    uuid=$(resolve_app_uuid "$key")

    local meta
    meta=$(cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/${uuid}/instances" | python3 -c '
import sys, json
rows = json.load(sys.stdin).get("data") or []
if not rows:
    sys.stderr.write("该应用没有实例\n"); sys.exit(1)
pick = next((p for p in rows if p.get("ready")), rows[0])
cs = pick.get("containers") or []
print(pick.get("namespace") or "", pick.get("name") or "", (cs[0].get("name") if cs else ""))
')
    ns=$(echo "$meta" | cut -d' ' -f1)
    pod=$(echo "$meta" | cut -d' ' -f2)
    container=$(echo "$meta" | cut -d' ' -f3)

    echo "# ns=${ns} pod=${pod} container=${container} tail=${lines}" >&2

    local raw
    raw=$(cloudos_raw "/api/cloud/app-mgmt/v1.0/namespaces/${ns}/pods/${pod}/log?container=${container}&tailingLines=${lines}&sinceSeconds=86400") \
        || die "HTTP 失败 GET pod log（${ns}/${pod}）"

    # 日志接口可能给 JSON（{"data":"<文本>"}）也可能给纯文本，两种都兜住
    local text
    text=$(printf '%s' "$raw" | python3 -c '
import sys, json
raw = sys.stdin.read()
try:
    d = json.loads(raw)
    if isinstance(d, dict):
        print(d.get("data") or "")
    else:
        print(raw)
except Exception:
    print(raw)
')

    if [[ -z "${text//[$'\n\t ']/}" ]]; then
        echo "（平台返回空日志 —— 红岛该接口 2026-10-23 实测恒为空，请改用 VictoriaLogs 或 itp-ops.sh log tail）" >&2
        return 2
    fi
    printf '%s\n' "$text"
}

# 启停重启 —— 生产写操作，必须显式 --yes（由入口解析为 OPT_YES）
app_action() {
    local key="$1" action="$2"
    case "$action" in
        start|stop|restart) ;;
        *) die "action 只能是 start|stop|restart（收到 ${action}）" ;;
    esac

    [[ "${OPT_YES:-0}" == "1" ]] \
        || die "这是生产环境写操作：app-act ${key} ${action}。确认无误请追加 --yes"

    local uuid
    uuid=$(resolve_app_uuid "$key")
    echo "即将对 ${key}（${uuid}）执行 ${action} ..." >&2

    cloudos_request PUT "/api/cloud/app-mgmt/v1.0/apps/${uuid}" \
        "$(printf '{"action":"%s"}' "$action")"
}

# ============================================================================
# 容器规格（CPU/内存；requests=调度保障、limits=运行上限）
#   GET  /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/resources
#   PUT  同路径，body 为数组 [{name, resources:{requests,limits}}]
#
#   ⚠ 该接口只返回/接受「业务容器」，istio-proxy 这类 sidecar 不在其中，别去动。
#   ⚠ PUT 改 resources 会触发 Deployment 滚动重建 Pod（生产有短暂容量下降）。
#   ⚠ 传的单位沿用平台自身格式（如 4000m / 8192Mi），别混 "4" / "8Gi"，
#     平台回显是 4000.0m / 8192.0Mi，用同一口径最稳。
# ============================================================================

# 单位归一：平台回显 4000.0m / 8192.0Mi，目标可能写 4000m / 8192Mi / 4 / 8Gi，
# 直接比字符串会把「已是目标值」误判成「待改」，故统一换算成 毫核 / Mi 再比。
_cpu_milli() {
    python3 -c 'import sys;v=str(sys.argv[1]).strip();print(int(float(v[:-1])) if v.endswith("m") else int(float(v)*1000))' "$1"
}
_mem_mi() {
    python3 -c '
import sys
v = str(sys.argv[1]).strip()
for u, f in (("Ki", 1/1024), ("Mi", 1.0), ("Gi", 1024.0), ("Ti", 1048576.0)):
    if v.endswith(u):
        print(int(float(v[:-len(u)]) * f)); sys.exit()
print(int(float(v) / 1024 / 1024))' "$1"
}

# 输出 "容器名|reqCpu|reqMem|limCpu|limMem"
_app_spec() {
    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/$1/resources" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or []
c = d[0] if d else {}
r = c.get("resources") or {}
q = r.get("requests") or {}
l = r.get("limits") or {}
print("%s|%s|%s|%s|%s" % (c.get("name"), q.get("cpu"), q.get("memory"), l.get("cpu"), l.get("memory")))
'
}

# 查看规格：单应用，或 all 看全量
app_spec() {
    local key="${1:-all}"

    if [[ "$key" != "all" ]]; then
        local uuid
        uuid=$(resolve_app_uuid "$key")
        _app_spec "$uuid" | awk -F'|' '{printf "%-22s req=%s/%s  limit=%s/%s\n", $1, $2, $3, $4, $5}'
        return 0
    fi

    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or []
for a in sorted(d, key=lambda x: x.get("name") or ""):
    print("%s\t%s" % (a.get("name"), a.get("uuid")))
' | while IFS=$'\t' read -r name uuid; do
        line=$(_app_spec "$uuid")
        echo "$line" | awk -F'|' -v n="$name" '{printf "%-22s req=%-9s %-10s limit=%-9s %s\n", n, $2, $3, $4, $5}'
    done
}

# 设规格（requests=limits 同值）
app_spec_set() {
    local key="$1" cpu="${2:-4000m}" mem="${3:-8192Mi}"

    local uuid cname cur
    uuid=$(resolve_app_uuid "$key")
    cur=$(_app_spec "$uuid")
    cname=$(echo "$cur" | cut -d'|' -f1)

    [[ -n "$cname" ]] || die "取不到容器名（${key}）"

    local cur_req_cpu cur_req_mem cur_lim_cpu cur_lim_mem
    IFS='|' read -r _ cur_req_cpu cur_req_mem cur_lim_cpu cur_lim_mem <<< "$cur"

    echo "${key}: 容器=${cname}  当前 req=${cur_req_cpu}/${cur_req_mem} limit=${cur_lim_cpu}/${cur_lim_mem}" >&2

    # 已是目标值就跳过（按数值比较，平台回显 4000.0m 与目标 4000m 等价）
    if [[ "$(_cpu_milli "$cur_req_cpu")" == "$(_cpu_milli "$cpu")" \
       && "$(_mem_mi "$cur_req_mem")" == "$(_mem_mi "$mem")" \
       && "$(_cpu_milli "$cur_lim_cpu")" == "$(_cpu_milli "$cpu")" \
       && "$(_mem_mi "$cur_lim_mem")" == "$(_mem_mi "$mem")" ]]; then
        echo "${key} 已是 req=limit=${cpu}/${mem}，跳过"
        return 0
    fi

    [[ "${OPT_YES:-0}" == "1" ]] \
        || die "生产写操作：${key} 改为 req=limit=${cpu}/${mem}（会滚动重启 Pod）。确认请追加 --yes"

    local body
    body=$(printf '[{"name":"%s","resources":{"requests":{"cpu":"%s","memory":"%s"},"limits":{"cpu":"%s","memory":"%s"}}}]' \
        "$cname" "$cpu" "$mem" "$cpu" "$mem")

    echo "${key}：下发规格 req=limit=${cpu}/${mem} ..." >&2
    cloudos_request PUT "/api/cloud/app-mgmt/v1.0/apps/${uuid}/resources" "$body" >/dev/null

    # 复验：平台 resources 接口**异步生效**（2026-10-26 红岛实测：PUT 返回
    # status=true 后立即读回仍是旧值，数十秒后才刷新）——所以 MUST 轮询到目标值，
    # NEVER 读一次就断定失败/成功（单次读回会给出「改了个寂寞」的假象）。
    local after="" i
    for i in $(seq 1 20); do
        after=$(_app_spec "$uuid")
        if [[ "$(_cpu_milli "$(echo "$after" | cut -d'|' -f2)")" == "$(_cpu_milli "$cpu")" \
           && "$(_mem_mi "$(echo "$after" | cut -d'|' -f3)")" == "$(_mem_mi "$mem")" \
           && "$(_cpu_milli "$(echo "$after" | cut -d'|' -f4)")" == "$(_cpu_milli "$cpu")" \
           && "$(_mem_mi "$(echo "$after" | cut -d'|' -f5)")" == "$(_mem_mi "$mem")" ]]; then
            echo "  ✓ 复验通过（第 ${i} 次探测）: $(echo "$after" | awk -F'|' '{printf "req=%s/%s limit=%s/%s", $2, $3, $4, $5}')" >&2
            break
        fi
        sleep 5
    done
    if [[ "$i" -ge 20 ]]; then
        echo "  ⚠ 复验超时 100s，平台仍报 $(echo "$after" | awk -F'|' '{printf "req=%s/%s limit=%s/%s", $2, $3, $4, $5}') —— MUST 用 kubectl 核实后再下结论" >&2
    fi

    echo "  ⚠ 规格变更会滚动重建 Pod，请用 pods 命令确认新副本 ready 后再动下一个" >&2
}

# 批量设规格：只处理与目标不一致的应用。--exclude 由本函数解析（--yes 已由入口收走）
spec_all() {
    local cpu="${1:-4000m}" mem="${2:-8192Mi}"
    [[ $# -ge 1 ]] && shift
    [[ $# -ge 1 ]] && shift
    local exclude=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --exclude) shift; exclude="${1:-}" ;;
            *)         die "未知参数 $1" ;;
        esac
        shift || true
    done

    echo "目标规格：req=limit=${cpu}/${mem}  排除：${exclude:-无}"
    echo ""

    local todo=0 skip=0
    local listing
    listing=$(cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or []
for a in sorted(d, key=lambda x: x.get("name") or ""):
    print("%s\t%s" % (a.get("name"), a.get("uuid")))
')

    local targets=()
    while IFS=$'\t' read -r name uuid; do
        [[ -z "$name" ]] && continue
        if [[ -n "$exclude" && ",${exclude}," == *",${name},"* ]]; then
            echo "  - ${name}（已排除）"
            continue
        fi
        local line req_cpu req_mem lim_cpu lim_mem
        line=$(_app_spec "$uuid")
        req_cpu=$(echo "$line" | cut -d'|' -f2)
        req_mem=$(echo "$line" | cut -d'|' -f3)
        lim_cpu=$(echo "$line" | cut -d'|' -f4)
        lim_mem=$(echo "$line" | cut -d'|' -f5)
        if [[ "$(_cpu_milli "$req_cpu")" == "$(_cpu_milli "$cpu")" \
           && "$(_mem_mi "$req_mem")" == "$(_mem_mi "$mem")" \
           && "$(_cpu_milli "$lim_cpu")" == "$(_cpu_milli "$cpu")" \
           && "$(_mem_mi "$lim_mem")" == "$(_mem_mi "$mem")" ]]; then
            skip=$((skip + 1))
            continue
        fi
        printf "  → %-22s req=%-9s %-9s limit=%-9s %s\n" "$name" "$req_cpu" "$req_mem" "$lim_cpu" "$lim_mem"
        targets+=("$name")
        todo=$((todo + 1))
    done <<< "$listing"

    echo ""
    echo "待改 ${todo} 个，已是目标 ${skip} 个"

    if [[ "$todo" -eq 0 ]]; then
        return 0
    fi
    if [[ "${OPT_YES:-0}" != "1" ]]; then
        echo "以上为预演（dry-run）。确认执行请追加 --yes"
        return 0
    fi

    local fail=0
    for n in "${targets[@]}"; do
        app_spec_set "$n" "$cpu" "$mem" --yes || fail=$((fail + 1))
    done
    echo ""
    [[ "$fail" -eq 0 ]] && echo "全部完成" || echo "⚠ ${fail} 个失败，MUST 逐个核实"
}

# 任意 REST 兜底
raw_call() {
    local method="$1" uri="$2" body="${3:-}"
    cloudos_request "$method" "$uri" "$body"
}

# ============================================================================
# 副本数调整（POST /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/scale）
#   平台侧是异步的：接口返回 status=true 只代表「已下发」，副本是否真起来要
#   轮询 instance_ok_num。所以 MUST 等待并复验，不能看 200 就走。
# ============================================================================

# 读回 "实例数 ready数 状态"
_app_inst() {
    cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps/$1" | python3 -c '
import sys, json
d = json.load(sys.stdin).get("data") or {}
print(d.get("instance_num"), d.get("instance_ok_num"), d.get("status"))
'
}

# 轮询到目标副本数且全部 ready（最多 180s）
_wait_replicas() {
    local uuid="$1" target="$2" i cur="" ok="" st=""
    for i in $(seq 1 60); do
        read -r cur ok st <<< "$(_app_inst "$uuid")" || true
        if [[ "$cur" == "$target" && "$ok" == "$target" && "$st" == "OK" ]]; then
            echo "  ✓ ${ok}/${target} ready, status=${st}（第 ${i} 次探测）" >&2
            return 0
        fi
        printf "  … %s/%s ready, status=%s（第 %d 次探测）\n" "${cur:-?}" "${ok:-?}" "${st:-?}" "$i" >&2
        sleep 3
    done
    echo "  ⚠ 等待超时 180s，当前 ${cur}/${ok} status=${st} —— MUST 登平台/查 kubectl 核实" >&2
    return 1
}

app_scale() {
    local key="$1" target="${2:-}"
    [[ "$target" =~ ^[0-9]+$ ]] || die "副本数必须是正整数（收到 '${target}'）"
    (( target >= 1 && target <= 20 )) || die "副本数应在 1..20（收到 ${target}）"

    local uuid cur ok st
    uuid=$(resolve_app_uuid "$key")
    read -r cur ok st <<< "$(_app_inst "$uuid")" || true

    if [[ "$cur" == "$target" ]]; then
        echo "${key} 已是 ${target} 实例（${ok} ready, status=${st}），跳过"
        return 0
    fi

    [[ "${OPT_YES:-0}" == "1" ]] \
        || die "生产写操作：${key} ${cur} → ${target} 实例。确认无误请追加 --yes"

    echo "${key}（${uuid}）：${cur} → ${target} 实例，已下发 ..." >&2
    # 平台实际行为与 API 文档不符（2026-10-23 实测）：
    #   body {"replicas":N}            → HTTP 500 application.5000006 Cannot deserialize ArrayList<McpPlaintext> from Object
    #   body [{"replicas":N}]          → HTTP 500 Required int parameter 'replicas' is not present
    #   body [] + ?replicas=N（本行）   → status=true  ✅
    # 即 replicas 走 query 参数，body 必须是 JSON 数组（平台按 McpPlaintext 列表反序列化）。
    cloudos_request POST "/api/cloud/app-mgmt/v1.0/apps/${uuid}/scale?replicas=${target}" "[]" >/dev/null

    _wait_replicas "$uuid" "$target"
}

# 批量：把 instance_num != target 的应用统一调到 target
scale_all() {
    local target="${1:-}" ; shift || true
    local exclude=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --exclude) shift; exclude="${1:-}" ;;
            *)         die "未知参数 $1" ;;
        esac
        shift || true
    done
    [[ "$target" =~ ^[0-9]+$ ]] || die "副本数必须是正整数（收到 '${target}'）"
    (( target >= 1 && target <= 20 )) || die "副本数应在 1..20"

    local plan
    plan=$(cloudos_request GET "/api/cloud/app-mgmt/v1.0/apps?page=1&pageSize=200" | python3 -c '
import sys, json
target = int(sys.argv[1])
excl = {x for x in (sys.argv[2] or "").split(",") if x}
rows = json.load(sys.stdin).get("data") or []
todo = [a for a in sorted(rows, key=lambda x: x.get("name") or "")
        if (a.get("name") not in excl) and a.get("instance_num") != target]
if not todo:
    print("NONE"); sys.exit(0)
for a in todo:
    print("%s\t%s\t%s\t%s\t%s" % (a.get("name"), a.get("uuid"), a.get("instance_num"),
                                  a.get("instance_ok_num"), a.get("status")))
' "$target" "$exclude")

    if [[ "$plan" == "NONE" ]]; then
        echo "全部应用已是 ${target} 实例，无需调整"
        return 0
    fi

    local n=0
    echo "待调整（→ ${target} 实例）："
    while IFS=$'\t' read -r name uuid cur ok st; do
        printf "  %-22s %-34s %s/%s → %s  status=%s\n" "$name" "$uuid" "$cur" "$ok" "$target" "$st"
        n=$((n + 1))
    done < <(printf '%s\n' "$plan")
    echo "共 ${n} 个应用"

    if [[ "${OPT_YES:-0}" != "1" ]]; then
        echo ""
        echo "以上为预演（dry-run）。确认执行请追加 --yes"
        return 0
    fi

    local fail=0
    while IFS=$'\t' read -r name uuid cur ok st; do
        app_scale "$uuid" "$target" --yes || fail=$((fail + 1))
    done < <(printf '%s\n' "$plan")

    echo ""
    [[ "$fail" -eq 0 ]] && echo "全部完成" || echo "⚠ ${fail} 个应用未达目标，MUST 逐个核实"
}

# ============================================================================
# 镜像构建（自 image-build.sh 迁移）—— 本地 jar + 自有 Dockerfile → 平台侧打镜像
#
# 【为什么不在本机打镜像】
#   开发机是 Apple Silicon，打 x86 镜像得靠 buildx + QEMU 模拟，耗时与磁盘代价都
#   不可接受（见 AGENTS.md §5.C）。本命令只做「传 jar + 提交构建」，镜像由平台的
#   x86 构建节点产出；同理排除 /images/load（要求本机 docker build + save）。
#
# 【与持续交付的区别（2026-10-23 实测结论）】
#   持续交付的 5 个预置 BUILD 模板全部以 CHECK_OUT（Git 检出）开头，拿不到源码
#   就跑不起来；PAAS_UPLOAD_ARTIFACT 证明软件中心的 jar 是流水线产物、不是输入。
#   所以「先传 jar 再让流水线打镜像」在平台上没有对应任务类型，走镜像仓库
#   POST /images/build。
#
# 【状态：未实测 —— 首次运行 MUST 先跑 image probe】
#   仍是推断、未在红岛验证的：fileFlag 语义（本命令成立与否全押在这里）。
#   文档三处出现该字段，没有一处定义它是什么；全文也搜不到 COPY /「构建上下文」。
#   判定方法见 cmd_probe 输出末尾。
# ============================================================================

image_prepare() {
    local mod="$1" ver="$2"
    [[ -n "$DOCKERFILE" ]] || DOCKERFILE="${ITP_OPS_ROOT}/docker/Dockerfile"
    local jar="${JAR_OVERRIDE:-${ITP_OPS_ROOT}/${mod}/target/${mod}-${ver}.jar}"
    [[ -f "$jar" ]] || die "找不到 jar：${jar}
   先构建：cd ${ITP_OPS_ROOT} && mise exec -- mvn -pl ${mod} -am clean package -DskipTests"
    [[ -f "$DOCKERFILE" ]] || die "找不到 Dockerfile：${DOCKERFILE}"

    # fat jar 校验：平台用 java -jar 拉起，必须是 spring-boot repackage 产物
    #
    # 【为什么要绕开管道（2026-10-26 修）】
    #   原写法 `unzip -l "$jar" | grep -q "BOOT-INF/classes"` 在本仓必假：
    #   入口设了 `set -uo pipefail`，而 grep -q 命中后立刻退出 → unzip 收到 SIGPIPE
    #   → unzip 退出码 9 → pipefail 把整条管道判为失败 → 误报「不是 fat jar」。
    #   实测：route-sentinel 1.0.1 确有 49 条 BOOT-INF/classes，手工同命令判真，
    #   放进带 pipefail 的脚本里就判假。**这会拦住所有模块的 image plan/build**。
    #   修法：先取全量清单到变量再匹配，不用管道（顺带避免 unzip 提前 SIGPIPE）。
    local manifest
    manifest=$(unzip -l "$jar" 2>/dev/null) || die "${jar} 不是合法的 zip（unzip 读取失败）"
    if ! printf '%s' "$manifest" | grep -q "BOOT-INF/classes"; then
        die "${jar} 不是 spring-boot fat jar（无 BOOT-INF/classes），平台无法 java -jar 启动"
    fi

    JAR="$jar"
    JAR_SIZE=$(stat -f %z "$JAR" 2>/dev/null || stat -c %s "$JAR")
    FILE_FLAG=$(pkg_gen_id)
}

cmd_probe() {
    log_info "== CloudOS 镜像构建能力探测（只读，不发写请求）=="
    log_info ""
    log_info "-- 0. 连通性预检（镜像服务可能在 11000，Web 网关在 443）"
    cloudos_check_ports "$CLOUDOS_HOST" 443 11000 || die "两个端口都不通 —— en9 是否 active？

   本机实测（2026-10-19 记录）：
     USB 网卡配到 K8s 控制区 172.120.2.x  → 才能通云管台
     配到出口区 / 走办公网 en0           → 只有 ICMP 通，TCP 全被拦
   查网卡：ifconfig en9 | grep inet"

    log_info ""
    log_info "-- 1. 登录（走 ${CLOUDOS_BASE_URL}）"
    local t
    t=$(cloudos_get_token) || die "登录失败（原因见上）"
    [[ -n "$t" ]] || die "登录未返回 token"
    log_info "   ✓ token 长度 ${#t}"

    log_info ""
    log_info "-- 2. 镜像服务基址（前缀固定 ${IMG_API_PREFIX}）"
    local base code best=""
    for base in "$CLOUDOS_BASE_URL" "http://${CLOUDOS_HOST}:11000" "https://${CLOUDOS_HOST}:11000"; do
        code=$(CLOUDOS_TARGET_BASE="$base" cloudos_http GET "${IMG_API_PREFIX}/types" | head -n1)
        case "$code" in
            401|403) log_info "   ${code}  GET ${base}${IMG_API_PREFIX}/types  ← 路由在"; best="$base" ;;
            404)     log_info "   ${code}  GET ${base}${IMG_API_PREFIX}/types  ← 无路由" ;;
            *)       log_info "   ${code}  GET ${base}${IMG_API_PREFIX}/types" ;;
        esac
    done
    [[ -n "$best" ]] && log_info "   ⇒ 用 IMG_BASE_URL=${best} 跑后续命令" \
                     || log_info "   ⇒ 都没路由：换探测路径，或确认平台版本是否含容器云模块"

    log_info ""
    log_info "-- 3. 镜像分类 UUID（构建必填 imgType）"
    local types
    types=$(IMG_BASE_URL="${best:-${IMG_BASE_URL:-}}" image_types)
    if [[ "$types" == "NETWORK_UNREACHABLE" || -z "$types" ]]; then
        log_info "   取不到（网络或路由问题）"
    else
        printf '%s\n' "$types" | awk -F'\t' '{printf "   uuid=%s  name=%s\n", $1, $2}'
    fi

    log_info ""
    log_info "-- 4. 已有镜像（确认 tag 占用 / 取回滚点）"
    local vers
    vers=$(IMG_BASE_URL="${best:-${IMG_BASE_URL:-}}" image_versions "$HARBOR_PROJECT")
    if [[ "$vers" == "NETWORK_UNREACHABLE" || -z "$vers" ]]; then
        log_info "   取不到（网络或路由问题）"
    else
        printf '%s\n' "$vers" | awk -F'\t' '{printf "   %-28s tagCount=%s  size=%sMB\n", $1, $2, $3}'
    fi

    log_info ""
    log_info "================ fileFlag 语义怎么验（决定本命令能否用）================"
    log_info "文档从未定义 fileFlag，也没有一处提到 Dockerfile 能否 COPY 外部文件。"
    log_info "最快判定法：跑一次最小构建，看返回 data.build[] 里 docker 原话："
    log_info ""
    log_info "  情况 1（可用）：日志出现 COPY/ADD 且有 context 大小"
    log_info "                  ⇒ fileFlag 就是构建上下文文件，本命令成立。"
    log_info "  情况 2（不成立）：报 COPY failed: file not found，或日志只有 FROM 一行"
    log_info "                  ⇒ fileFlag 只是任务标识，镜像仓库装不进 jar。"
    log_info "                     转而用软件中心的 jar 类型部署（平台自带 JDK 镜像，x86）。"
    log_info ""
    log_info "最小构建的 Dockerfile 只写两行："
    log_info "  FROM ${HARBOR_PROJECT}/jdk:21"
    log_info "  COPY app.jar /app.jar"
}

cmd_plan() {
    local mod="$1" ver="$2"
    image_prepare "$mod" "$ver"
    log_info "================ 将要执行（dry-run，未发任何写请求）================"
    log_info "服务        : ${mod}"
    log_info "版本        : ${ver}"
    log_info "jar         : ${JAR}  (${JAR_SIZE} bytes)"
    log_info "Dockerfile  : ${DOCKERFILE}"
    log_info "fileFlag    : ${FILE_FLAG}   ← 推断：客户端预生成的文件标识（未验证）"
    log_info "镜像        : ${HARBOR_PROJECT}/${mod}:${ver}"
    log_info "构建接口    : POST ${IMG_BASE_URL:-$CLOUDOS_BASE_URL}${IMG_API_PREFIX}/images/build"
    log_info ""
    log_info "-- 上传用字段（POST /api/cloud/deploy/v1.0/packages, multipart）"
    log_info "   id=${FILE_FLAG}  pkgType=jar  name=${mod}  version=${ver}"
    log_info "   file=@${JAR}   filename 固定 app.jar（对齐 COPY app.jar）"
    log_info ""
    log_info "-- 构建用 body"
    log_info "   dockerfile = $(python3 -c 'import sys;print(open(sys.argv[1],encoding="utf-8").read()[:120])' "$DOCKERFILE")…"
    log_info "   imgName    = ${mod}"
    log_info "   version    = ${ver}"
    log_info "   imgType    = ${IMG_TYPE_UUID:-<probe 取得>}"
    log_info "   type       = private"
    log_info "   project    = ${HARBOR_PROJECT}"
    log_info "   fileFlag   = ${FILE_FLAG}"
    log_info ""
    log_info "-- 前置检查（MUST 人工确认）"
    log_info "   [ ] Harbor 里 ${mod} 已有可回滚的上一个 tag"
    log_info "   [ ] 目标 tag ${ver} 未被占用（部署纪律：版本末位 +1，NEVER 同 tag 覆盖）"
    log_info "   [ ] 当前 pod 状态允许滚更（itp-ops.sh cloudos pods ${mod}）"
    log_info "   [ ] IMG_TYPE_UUID 已从 probe 获取"
    log_info ""
    log_info "-- 架构（ARM 开发机的关键约束）"
    log_info "   本机只出 jar（字节码与架构无关），镜像由平台 x86 构建节点打。"
    log_info "   但 ${IMG_API_PREFIX}/images/build 的 body **没有 architecture 参数**"
    log_info "   （持续交付 BUILD_IMAGE 任务才有），镜像架构由构建节点决定、不可指定。"
    log_info "   构建完 MUST 核对产物 arch："
    log_info "     docker manifest inspect ${HARBOR_PROJECT}/${mod}:${ver} | grep architecture"
    log_info "   期望 amd64。拿到 arm64 说明构建节点是 ARM，x86 业务节点拉不起来。"
    log_info ""
    log_info "确认无误后：itp-ops.sh cloudos image build ${mod} ${ver} --yes"
}

cmd_build() {
    local mod="$1" ver="$2"
    image_prepare "$mod" "$ver"

    [[ "${OPT_YES:-0}" == "1" ]] \
        || die "build 会向生产平台发写请求，必须显式带 --yes（先跑 image plan 看清单）"
    [[ -n "${IMG_TYPE_UUID:-}" ]] || die "缺 IMG_TYPE_UUID —— 先跑 itp-ops.sh cloudos image probe 取镜像分类 UUID"

    log_info "== 1/4 上传 jar（fileFlag=${FILE_FLAG}）"
    pkg_upload "$JAR" "$mod" "$ver" "$FILE_FLAG" "app.jar" \
        || die "上传失败"
    log_info "   ✓ 上传接口不回 ID，fileFlag 用预生成值"

    # 回查确认落盘（大小对得上是上传自检的唯一手段）
    local found
    if found=$(pkg_find_version "$mod" "$ver" 2>/dev/null); then
        log_info "   ✓ 回查版本 ID=$(printf '%s' "$found" | cut -f1)  文件=$(printf '%s' "$found" | cut -f2)"
    else
        log_info "   ⚠ 回查不到该版本 —— 上传可能未落盘，继续构建大概率失败"
    fi

    log_info "== 2/4 提交镜像构建（平台 x86 节点 docker build & push）"
    local dockerfile_text out code body
    dockerfile_text=$(cat "$DOCKERFILE")
    out=$(image_build "$dockerfile_text" "$mod" "$ver" "$IMG_TYPE_UUID" "$FILE_FLAG" "$HARBOR_PROJECT" \
        "built from local jar via itp-ops.sh cloudos image")
    code=$(printf '%s' "$out" | head -n1)
    body=$(printf '%s' "$out" | sed '1d')

    _is_net_fail "$code" && die "构建请求未发出（网络不可达）"
    [[ "$code" == "200" ]] || die "构建 HTTP ${code}：${body:0:600}"

    printf '%s' "$body" | python3 -c '
import sys, json
d = json.load(sys.stdin)
if d.get("status") is not True:
    sys.stderr.write("构建失败：code=%s msg=%s\n" % (d.get("code"), d.get("msg"))); sys.exit(1)
data = d.get("data") or {}
build, push = data.get("build") or [], data.get("push") or []
digest = None
for e in push:
    digest = (e.get("aux") or {}).get("Digest") or digest
    st = e.get("status") or ""
    if "digest:" in st:
        digest = st.split("digest:")[-1].strip().split()[0]

# build 日志里出现 COPY/ADD 才说明 fileFlag 是构建上下文文件
copy_seen = any(("COPY" in (e.get("stream") or "")) or ("ADD" in (e.get("stream") or "")) for e in build)
print("   ✓ 构建+推送完成")
print("   docker 日志里 COPY/ADD：%s" % ("出现 ✓ ⇒ fileFlag 是上下文文件" if copy_seen else "未出现 ⚠ 见文件尾缺口 1"))
print("   digest =", digest or "（未在响应里找到，去 Harbor 核对）")
if not digest: sys.exit(2)
' || log_info "   ⚠ 响应里没找到 digest —— 判据是 Harbor 里有该 tag，不是 HTTP 200"

    log_info "== 3/4 回查镜像版本"
    local detail
    if detail=$(image_detail "$mod" "$HARBOR_PROJECT" 2>/dev/null); then
        printf '%s\n' "$detail" | awk -F'\t' '{printf "   %-20s %-12s %s\n", $1, $2, $3}'
    else
        log_info "   回查失败，去 Harbor 页面核对"
    fi

    log_info "== 4/4 滚更应用（本命令不做）"
    log_info "   只负责把镜像推到 Harbor；应用侧仍走既有发布流程："
    log_info "   itp-ops.sh cloudos app ${mod}  → 确认当前镜像 → 平台改 image tag 滚更"
    log_info ""
    log_info "回滚：把应用 image tag 指回上一个可用版本（Harbor 里旧 tag 未删除）"
}

# ============================================================================
# 测试环境附加命令（自 cloudos-api.sh 迁移；--env test 才有意义）
#   原脚本自带一套「base64 明文」认证，2026-10-23 已被抓包证伪（平台实际按 rsa
#   字段决定是否解密），现统一走 lib 的 RSA 认证。原「head -n -1」BSD 不支持，
#   已由 lib 的 sed '$d' 取代。
# ============================================================================

# 容器云前缀：文档 §容器云 实为镜像服务 /api/cloud/hcr/v1.0；pod 级 API 的前缀
# 从未实测过（测试环境本机不可达），保留变量便于将来校准，NEVER 当结论用。
CONTAINER_API_PREFIX="${CONTAINER_API_PREFIX:-/api/cloud/container/v1.0}"

cloudos_appgroups() { cloudos_request GET "/api/cloud/app-mgmt/v1.0/appgroups"; }

cloudos_pod_list() {
    local ns="${1:-}"
    local uri="${CONTAINER_API_PREFIX}/pods"
    [[ -n "$ns" ]] && uri="${uri}?namespace=${ns}"
    cloudos_request GET "$uri"
}
cloudos_pod_get()    { cloudos_request GET "${CONTAINER_API_PREFIX}/pods/$1"; }
cloudos_pod_delete() { cloudos_request DELETE "${CONTAINER_API_PREFIX}/pods/$1"; }

cloudos_svc_clusters()      { cloudos_request GET "/api/cloud/svcgw/v1.0/kaas_clusters"; }
cloudos_svc_cluster_nodes() { cloudos_request GET "/api/cloud/svcgw/v1.0/kaas_clusters/$1"; }

# 软件中心 jar 包：list/get 用 lib 的 pkg_*（比原脚本多回查与自检）
cloudos_pkg_list() {
    local page="${1:-1}" size="${2:-20}"
    cloudos_request GET "${PKG_API_PREFIX}/packages?page=${page}&size=${size}"
}
cloudos_pkg_get() { cloudos_request GET "${PKG_API_PREFIX}/packages/$1"; }
cloudos_pkg_upload() {
    local file="$1" name="${2:-$(basename "$1")}" ver="${3:-v1}"
    [[ -f "$file" ]] || die "文件不存在：${file}"
    # id 客户端预生成（上传接口不回 ID；旧测试脚本用 name-version 拼，格式不符平台短 ID）
    local id; id=$(pkg_gen_id)
    pkg_upload "$file" "$name" "$ver" "$id" || die "上传失败"
    log_info "   ✓ 已上传（id=${id}；回查：itp-ops.sh cloudos pkg-list，按 name+version 找）"
}

# image plan/build 共用参数解析：flag 与位置参数可任意顺序，位置参数按出现顺序取前两个
# （旧 image-build.sh 在入口处解析，行为等价；这里收进域内，入口只留全局参数）
image_parse_args() {
    IMG_MOD=""; IMG_VER=""
    local positional=()
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --jar)        [[ $# -ge 2 ]] || die "--jar 缺值"; JAR_OVERRIDE="$2"; shift 2 ;;
            --project)    [[ $# -ge 2 ]] || die "--project 缺值"; HARBOR_PROJECT="$2"; shift 2 ;;
            --img-type)   [[ $# -ge 2 ]] || die "--img-type 缺值"; IMG_TYPE_UUID="$2"; shift 2 ;;
            --dockerfile) [[ $# -ge 2 ]] || die "--dockerfile 缺值"; DOCKERFILE="$2"; shift 2 ;;
            --keep-pkg)   shift ;;   # 兼容旧入口：上传的包默认不删，此 flag  historically 是 no-op
            --)           shift; while [[ $# -gt 0 ]]; do positional+=("$1"); shift; done ;;
            --*)          die "未知选项 $1" ;;
            *)            positional+=("$1"); shift ;;
        esac
    done
    IMG_MOD="${positional[0]:-}"
    IMG_VER="${positional[1]:-}"
}

# ============================================================================
# 帮助
# ============================================================================
cloudos_usage() {
    cat <<EOF
itp-ops.sh cloudos —— CloudOS 平台运维（当前环境：${CLOUDOS_ENV:-hongdao} / ${CLOUDOS_HOST:-未初始化}）

用法: itp-ops.sh [--env hongdao|test] cloudos <命令> [参数...]

认证与诊断
  health                         连通性自检（网卡/TCP/登录/业务），写操作前必跑
  auth-debug                     登录诊断（密文长度、公钥指纹、token）
  logout                         清本环境 token 缓存

企业应用（红岛 itpb；对应控制台 /enterprise-apps/#/apply/app/appDetail/<uuid>）
  apps [status]                  应用列表；status 可选 OK/Error/Stopped...
  app       <名字|uuid>          应用详情（含 env 全量）
  env       <名字|uuid>          只打 env（排查 SERVICE_*_URL 是否指向 itp）
  svc       <名字|uuid>          访问方式与 NodePort 映射
  pods      <名字|uuid>          实例列表（podIP / ready / 重启次数）
  logs      <名字|uuid> [行数]   平台侧运行日志（默认 200 行；红岛该接口恒空，
                                 主力手段是 itp-ops.sh log tail <svc>）
  act       <名字|uuid> start|stop|restart --yes   生产写操作

副本数（异步，自动轮询到 ready）
  scale     <名字|uuid> <副本数> --yes            调单个；已是目标则跳过
  scale-all <副本数> [--yes] [--exclude a,b]      批量；不带 --yes 是预演

容器规格（requests=limits，改完会滚动重启 Pod）
  spec      [名字|uuid|all]      查看规格（默认 all）
  spec-set  <名字|uuid> [cpu] [mem] [--yes]       默认 4000m / 8192Mi（= 4C/8G）
  spec-all  [cpu] [mem] [--yes] [--exclude a,b]   批量对齐；不带 --yes 是预演

镜像构建（本地 jar → 平台 x86 节点打镜像；只传 jar，代码不出内网）
  image probe                    只读探测（首次必跑：基址/镜像分类/已有 tag）
  image plan  <模块> <版本>       dry-run，打印将提交的全部参数
  image build <模块> <版本> --yes 真执行（写生产）
        附加选项：--jar <p> --project <name> --img-type <uuid> --dockerfile <p>

测试环境附加（--env test；前缀未经实测，见域头注）
  appgroups                      应用组列表
  pod-list [ns] / pod-get <pod> / pod-delete <pod>      容器云 pod（前缀待校准）
  svc-clusters / svc-cluster-nodes <uuid>               服务治理
  pkg-list [page] [size] / pkg-get <id> / pkg-upload <file> [name] [version]
                                 软件中心 jar 包

兜底
  raw <METHOD> <URI> [BODY]      任意 REST，例：raw GET /api/cloud/app-mgmt/v1.0/apps

示例
  itp-ops.sh cloudos health
  itp-ops.sh cloudos apps
  itp-ops.sh cloudos env fep-app
  itp-ops.sh cloudos scale web-admin 1 --yes
  itp-ops.sh cloudos image probe

环境变量覆盖
  CLOUDOS_HOST / CLOUDOS_USERNAME / CLOUDOS_PASSWORD / CLOUDOS_DOMAIN /
  CLOUDOS_TIMEOUT / CLOUDOS_RSA_PUBKEY_B64 / IMG_BASE_URL / TOKEN_CACHE
EOF
}

# ============================================================================
# 主入口（由 itp-ops.sh 调用；全局参数 --env/--yes 已被入口收走）
# ============================================================================
cloudos_main() {
    local cmd="${1:-}"; shift || true

    # 无参数 / 帮助：不需要环境初始化（测试环境公钥未配时也能看用法）
    case "$cmd" in
        ""|-h|--help|help) cloudos_usage; return 0 ;;
    esac

    # 默认 hongdao（红岛是主战场，且历史上就是默认值）。
    # itp_env_init 会按 --env 设定 CLOUDOS_ENV 等，cloudos_env_init 再据此取凭据。
    itp_env_init hongdao || return 1
    cloudos_env_init || return 1
    printf '== 环境：%s（%s / 账号 %s）==\n' \
        "${CLOUDOS_ENV:-hongdao}" "$CLOUDOS_BASE_URL" "$CLOUDOS_USERNAME" >&2

    case "$cmd" in
        auth-debug|auth_debug) auth_debug ;;
        health|check)        health ;;
        logout)              rm -f "$TOKEN_CACHE"; echo "已清 token 缓存 ${TOKEN_CACHE}" ;;
        apps)                apps "${1:-}" ;;
        app)                 app_detail "${1:?需要应用名或 uuid}" ;;
        env)                 app_env "${1:?需要应用名或 uuid}" ;;
        svc|service)         app_service "${1:?需要应用名或 uuid}" ;;
        pods|instances)      app_pods "${1:?需要应用名或 uuid}" ;;
        logs)                app_logs "${1:?需要应用名或 uuid}" "${2:-200}" ;;
        act|action)          app_action "${1:?需要应用名或 uuid}" "${2:?需要 start|stop|restart}" ;;
        scale)               app_scale "${1:?需要应用名或 uuid}" "${2:?需要副本数}" ;;
        scale-all)           scale_all "$@" ;;
        spec)                app_spec "${1:-all}" ;;
        spec-set)            app_spec_set "${1:?需要应用名或 uuid}" "${2:-4000m}" "${3:-8192Mi}" ;;
        spec-all)            spec_all "$@" ;;
        raw)                 raw_call "${1:?需要 METHOD}" "${2:?需要 URI}" "${3:-}" ;;
        appgroups)           cloudos_appgroups ;;
        pod-list)            cloudos_pod_list "${1:-}" ;;
        pod-get)             cloudos_pod_get "${1:?需要 pod 名}" ;;
        pod-delete)          cloudos_pod_delete "${1:?需要 pod 名}" ;;
        svc-clusters)        cloudos_svc_clusters ;;
        svc-cluster-nodes)   cloudos_svc_cluster_nodes "${1:?需要集群 uuid}" ;;
        pkg-list)            cloudos_pkg_list "${1:-1}" "${2:-20}" ;;
        pkg-get)             cloudos_pkg_get "${1:?需要版本 id}" ;;
        pkg-upload)          cloudos_pkg_upload "$@" ;;
        image)
            local sub="${1:-}"; shift || true
            case "$sub" in
                probe) cmd_probe ;;
                plan)
                    image_parse_args "$@"
                    [[ -n "$IMG_MOD" && -n "$IMG_VER" ]] || die "用法：image plan <module> <version>"
                    cmd_plan "$IMG_MOD" "$IMG_VER" ;;
                build)
                    image_parse_args "$@"
                    [[ -n "$IMG_MOD" && -n "$IMG_VER" ]] || die "用法：image build <module> <version> --yes"
                    cmd_build "$IMG_MOD" "$IMG_VER" ;;
                *) die "image 子命令只能是 probe / plan / build（收到 ${sub}）" ;;
            esac ;;
        *) echo "未知命令：${cmd}" >&2; cloudos_usage; return 1 ;;
    esac
}

# ============================================================================
# 已知缺口（首次实测前勿当结论）
#   1. fileFlag 语义未验证。若平台要求它由某个上传接口返回而非客户端生成，
#      pkg_upload 会失败或 build 报 file not found；判定看 data.build[] 的 COPY。
#      不成立时的正路是软件中心 jar 类型部署（平台自带 JDK 镜像，x86 天然满足）。
#   2. totalChunks=1 一次性上传。jar 超过网关 body 限制（经验值 ~100MB）需改分片；
#      分片语义由平台按 id+chunkNumber 判定，续传/去重行为未实测。
#   3. 上传会在软件中心留一条 jar 应用包记录（POST /packages 的副作用）。
#      不想留用 pkg_delete_version <pkg_uuid> <version_id> 清；默认不删，
#      避免误删别人的包。
#   4. 镜像服务的网关基址未定位（2026-10-23 网络中断）。probe 就是为此写的。
#   5. 构建日志走 data.build[]（docker 原始输出），平台无错误码表，失败只能读这里。
#   6. 镜像架构不可指定（见 plan 的「架构」段），构建后 MUST 核对 amd64。
#   7. 测试环境（--env test）RSA 公钥未抓到，所有命令会停在 env_init 报错；
#      容器云 pod_* 前缀 /api/cloud/container/v1.0 亦未经实测。
# ============================================================================
