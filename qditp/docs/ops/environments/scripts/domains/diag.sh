#!/bin/bash
# ============================================================================
# itp-ops · diag 域 —— 网络可达性自检 / 入向路由全链路导出
#
# 【本文件是什么】
#   2026-10-25 重构：原 ops/red-island-reachability.sh（红岛分区可达性自检）
#   + ops/dump-48000-routes.sh（48000 入向路由全链路导出）二合一。
#   输出逐条保留原样；修掉两个旧缺陷：
#     1. dump-48000-routes.sh 的基线目录用 `$(dirname $0)/migration/...`，
#        迁目录后路径失效，被 `if [ -f ]` 静默跳过 —— 用户以为 diff 过、
#        其实什么都没比。现在指向 migration/ingress-config-cleaned 且缺失时
#        显式告警，NEVER 静默。
#     2. 连通性预检复用 lib/remote.sh 的 BatchMode 预检（不弹密码提示）。
#
# 【环境变量】
#   DIAG_IF   可达性自检的网卡名（默认 en9，本机 USB 网卡 AX88179B）
#   SSH_HOST  覆盖跳板机（默认走 lib/remote.sh 的 REMOTE_HOST）
#
# 【依赖】lib/core.sh、lib/remote.sh
# ============================================================================

# 兼容旧环境变量名 SSH_HOST（旧 dump-48000-routes.sh 用它）
REMOTE_HOST="${SSH_HOST:-${REMOTE_HOST}}"
DIAG_IF="${DIAG_IF:-en9}"

# ---------------------------------------------------------------- 用法
diag_usage() {
    cat <<EOF
itp-ops.sh diag —— 网络可达性自检 / 入向路由导出

用法: itp-ops.sh diag <命令> [参数...]

命令
  reach                       红岛分区可达性自检：看 USB 网卡 ${DIAG_IF} 拿到哪个区的
                              IP，逐区探 ICMP + TCP 端口，判定「插到哪个区、
                              哪些区通了」。插好网卡后跑一次即可。
  routes [输出目录]           导出 ITP 公网 48000 入向路由全链路配置到文件：
                              ns itp-gateway 的网关 + ns itp 的 Gateway/VirtualService，
                              含 envoy 实际下发路由，并与仓库基线快照 diff。
                              默认输出到 ~/itp-snapshots/48000-routes-<时间戳>/。
  help                        本帮助

前置与说明
  · routes 导出的是「集群当前值」，不是仓库基线；diff 段用来自动比对基线快照是否漂移。
  · routes 前置：本机能 ssh ${REMOTE_HOST}（测试环境 172.20.211.21:22），不通就先换公司网 / 进 VPN。
  · reach 判读口诀：三项关键服务全 ✅ 才全通；插在出口区 .249 是隔离的，换口即可。
EOF
}

# ---------------------------------------------------------------- reach（原 red-island-reachability.sh）
diag_reach() {
    local IF="$DIAG_IF"
    echo "=== 1. 网卡 $IF 当前 IP（决定你插到哪个红岛区）==="
    local ip
    ip=$(ifconfig "$IF" 2>/dev/null | awk '/inet /{print $2}' | head -1)
    if [ -z "$ip" ]; then
        echo "  ⚠️  $IF 没拿到 IP——USB 没插好或没插业务口。插到红岛 K8s 控制区 172.120.2.x 口后重试。" >&2
        return 1
    fi
    echo "  $IF = $ip"
    case "$ip" in
        172.120.2.*)   echo "  → K8s 控制区（可直达 hd-linux:22 / VL:31920）" ;;
        172.120.110.*) echo "  → 业务区（可直达 前端:31919）" ;;
        172.120.249.*) echo "  ⚠️  出口区（隔离！到 K8s/业务无三层路由，只能 ping 本网段网关）" ;;
        *) echo "  → 未知区（$ip），逐区探测看哪些通" ;;
    esac

    echo ""
    echo "=== 2. 逐区可达性（ping + TCP 端口）==="
    # 每个区：| 标签 | 代表 host | 要探的端口
    local entry label host ports icmp pout p
    for entry in \
      "K8s控制|172.120.2.13|22|31920" \
      "K8s工作|172.120.1.11|22" \
      "云管|172.120.3.10|22" \
      "业务|172.120.110.11|31919|31920" \
      "私有|172.120.125.1|22" \
      "安全|172.120.209.101|22" \
      "工作站|172.120.211.1|22" \
      "核心|172.120.216.1|22" \
      "出口|172.120.249.254|" \
      "管理|172.120.250.1|22" \
      "环回|172.120.255.1|22" \
    ; do
        IFS='|' read -r label host ports <<< "$entry"
        # ping 判定
        if ping -c1 -t1 "$host" >/dev/null 2>&1; then icmp=OK; else icmp=FAIL; fi
        # 逐端口
        pout=""
        for p in $ports; do
            if [ -z "$p" ]; then continue; fi
            if nc -z -G 3 "$host" "$p" >/dev/null 2>&1; then pout="$pout $p:OK"; else pout="$pout $p:FAIL"; fi
        done
        printf "  %-8s %-18s ICMP=%-4s%s\n" "$label" "$host" "$icmp" "$pout"
    done

    echo ""
    echo "=== 3. 关键服务探活（前端 / VL / hd-linux SSH）==="
    local target port
    for target in "hd-linux SSH|172.120.2.13|22" "VL|172.120.2.13|31920" "前端 web|172.120.110.11|31919"; do
        IFS='|' read -r label host port <<< "$target"
        if nc -z -G 5 "$host" "$port" >/dev/null 2>&1; then
            echo "  ✅ $label $host:$port 可达"
        else
            echo "  ❌ $label $host:$port 不可达"
        fi
    done

    echo ""
    echo "=== 4. 结论 ==="
    echo "  若 3 全 ✅：已全通，可 ssh hd-linux 拉集群 + 回写 MEMORY"
    echo "  若 hd-linux/VL ✅ 但 前端 ❌：说明前端在 172.120.110.x 业务区，你的 ${IF} 在 .2 控制区，跨区不通——需红岛侧放行 .2↔.110 三层路由"
    echo "  若 3 全 ❌：你插在出口区 .249（隔离区），换插 K8s 控制区 .2 或 业务区 .110 的口"
}

# ---------------------------------------------------------------- routes（原 dump-48000-routes.sh）
diag_routes() {
    local TS OUT
    TS=$(date +%Y%m%d-%H%M%S)
    OUT="${1:-$HOME/itp-snapshots/48000-routes-$TS}"

    echo "=== 0. 连通性预检（${REMOTE_HOST}）==="
    if ! remote_reachable; then
        echo "  ⚠️  ssh ${REMOTE_HOST} 不通，未生成任何文件。换公司网 / 进 VPN 后重跑。" >&2
        return 1
    fi
    echo "  OK"
    mkdir -p "$OUT" || return 1

    run() { remote_exec "$1"; }

    echo "=== 1. 导出对象 yaml（--show-managed-fields=false，剥运行时噪声）==="
    dump() {
        local kind=$1 name=$2 ns=$3 file=$4
        if run "kubectl get $kind $name -n $ns -o yaml --show-managed-fields=false" > "$OUT/$file" 2>"$OUT/.err"; then
            echo "  ✓ $file"
            rm -f "$OUT/.err"
        else
            echo "  ✗ $file（$(head -1 "$OUT/.err" 2>/dev/null)）"
            rm -f "$OUT/$file" "$OUT/.err"
        fi
    }
    dump vs      fep-app-vr            itp         fep-app-vr.yaml
    dump gateway accountserver-gw      itp         accountserver-gw.yaml
    dump svc     itp-gateway           itp-gateway itp-gateway-service.yaml
    dump svc     itp-gateway-tgrce-svc itp-gateway itp-gateway-tgrce-svc.yaml
    dump deploy  itp-gateway           itp-gateway itp-gateway-deployment.yaml

    echo "=== 2. 路由摘要（spec.http 下标 = 行号 - 1）==="
    run "kubectl get vs fep-app-vr -n itp -o jsonpath='{range .spec.http[*]}{.match[0].uri.prefix}{\" -> \"}{.route[0].destination.host}{\":\"}{.route[0].destination.port.number}{\"  rewrite=\"}{.rewrite.uri}{\"\n\"}{end}'" \
      | cat -n | tee "$OUT/routes.txt"

    echo "=== 3. envoy 实际下发路由（VS 写对了 ≠ 已下发）==="
    local POD CT
    POD=$(run "kubectl get pod -n itp-gateway -o jsonpath='{.items[0].metadata.name}'" | tr -d '\r')
    if [ -n "$POD" ]; then
        CT=$(run "kubectl get pod $POD -n itp-gateway -o jsonpath='{.spec.containers[0].name}'" | tr -d '\r')
        echo "  pod=$POD container=$CT"
        run "kubectl exec -n itp-gateway $POD -c $CT -- curl -s '127.0.0.1:15000/config_dump?resource=dynamic_route_configs'" \
          > "$OUT/envoy-route-config-dump.json" 2>/dev/null
        python3 - "$OUT/envoy-route-config-dump.json" > "$OUT/envoy-routes.txt" <<'PY'
import json, sys
try:
    data = json.load(open(sys.argv[1]))
except Exception as e:
    print("解析失败：%s" % e); raise SystemExit(0)
rows, seen = [], set()
def walk(o):
    if isinstance(o, dict):
        m, r = o.get("match"), o.get("route")
        if isinstance(m, dict) and isinstance(r, dict):
            p = m.get("path") or m.get("prefix") or m.get("safe_regex", {}).get("regex", "")
            c = r.get("cluster") or ((r.get("weighted_clusters") or {}).get("clusters") or [{}])[0].get("name", "")
            if c and (p, c) not in seen:
                seen.add((p, c)); rows.append((p, c))
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
walk(data)
for p, c in rows:
    print("%-34s -> %s" % (p, c))
PY
        echo "  ✓ envoy-routes.txt（$(wc -l < "$OUT/envoy-routes.txt" | tr -d ' ') 条）"
    else
        echo "  ✗ 取不到 itp-gateway pod，跳过"
    fi

    echo "=== 4. 与仓库基线快照 diff（只比 VS/Gateway）==="
    # 修复记录：旧版用 $(dirname $0)/migration/...，脚本迁进 domains/ 后失效，
    # 被 [ -f ] 静默跳过（看着 diff 过、实际没比）。现在显式定位 + 缺失告警。
    local BASE="${ITP_OPS_DIR}/../migration/ingress-config-cleaned"
    if [ ! -d "$BASE" ]; then
        log_warn "基线目录缺失：${BASE} —— 跳过 diff 段（NEVER 静默：没比过基线就是没比过）"
    else
        # 2026-10-26 审计：基线快照本身已过期（fep-app-vr.yaml 只有 8 条 prefix，
        # 真值是 7 条 SCG exact + 8 条 prefix）。用它 diff 会把已上线路由误报成漂移，
        # 所以每次比对前都显式告警——NEVER 让「diff 有输出」被当成发现真问题。
        log_warn "⚠️ 基线快照可能已过期：2026-10-26 审计发现 fep-app-vr.yaml 缺 7 条 SCG exact 路由（真值 15 条）。"
        log_warn "   diff 报出的差异 MUST 先排除基线陈旧因素；重抓方法见 migration/ingress-config-cleaned/README.md"
        local f
        for f in fep-app-vr.yaml accountserver-gw.yaml; do
            if [ -f "$BASE/$f" ] && [ -f "$OUT/$f" ]; then
                if diff -u "$BASE/$f" "$OUT/$f" > "$OUT/$f.diff" 2>&1; then
                    echo "  = $f 与基线一致"
                    rm -f "$OUT/$f.diff"
                else
                    echo "  ≠ $f 有差异（见 $f.diff，通常只是 creationTimestamp/resourceVersion/uid）"
                fi
            else
                log_warn "基线或导出缺 $f（base=$([ -f "$BASE/$f" ] && echo 有 || echo 无) out=$([ -f "$OUT/$f" ] && echo 有 || echo 无)），跳过"
            fi
        done
    fi

    echo ""
    echo "输出目录：$OUT"
    ls -1 "$OUT" | sed 's/^/  /'
}

# ---------------------------------------------------------------- 域入口
diag_main() {
    need_cmd ssh
    local cmd="${1:-}"; shift || true
    case "$cmd" in
        ""|help|-h|--help) diag_usage; return 0 ;;
        reach)
            # 纯红岛：探测目标全是 172.120.x 网段（见 diag_reach 内区段表）
            itp_env_init hongdao || return 1
            itp_env_banner "红岛分区可达性（K8s 控制区 / 业务网 / 出口区）"
            diag_reach "$@" ;;
        routes)
            # 纯测试环境：抓的是 ns itp / itp-gateway 的 VS/Gateway（红岛是 itpb + ns istio-ingress）
            itp_env_init test || return 1
            itp_env_banner "入向路由导出（ns ${K8S_NS}，入口 ${INGRESS_ENTRY}）"
            need_cmd python3; diag_routes "$@" ;;
        *)      echo "未知命令：${cmd}" >&2; diag_usage; return 1 ;;
    esac
}
