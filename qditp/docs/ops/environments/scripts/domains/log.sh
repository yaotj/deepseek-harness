#!/bin/bash
# ============================================================================
# itp-ops · log 域 —— K8s 服务日志查看与监控
#
# 【本文件是什么】
#   2026-10-25 重构：原 ops/klog.sh（日志查看代理）+ ops/klog-watcher.sh
#   （Pod 重启自动重挂监控）二合一为一个域。
#   命令与输出逐条保留原样；修掉三个旧缺陷：
#     1. KLOG_CONTAINER 以前只在帮助里写着「能用」，实际 ssh 不转发本地环境变量，
#        设了也到不了远端 —— 现在显式拼进远端命令前缀，真正生效。
#     2. watcher 状态文件写入格式是 `svc=key`，查找却用 `grep "^svc|"`，
#        永远匹配不到 ⇒ 每轮都判定「状态变化」，把全部监控杀一遍再起一遍
#        （每 30 秒一次）。已改为按写入格式查找。
#     3. watcher 杀监控只杀本地 bash 壳，ssh 子进程成漏网之鱼（remote tail -F
#        永不退出），多轮重启会累积。改为按记录的 PID 杀进程树（bash + ssh）。
#
# 【TTY 纪律（MUST）】
#   tail 一律走 ssh -t（remote_exec_tty）：-f 实时跟踪 / Ctrl+C / 颜色 /
#   逐行刷新都依赖伪终端。NEVER 改成非交互 ssh。
#
# 【参数转义纪律（MUST）】
#   远端命令是拼字符串执行的，NEVER 直接拼 "$@"——服务名/关键字含空格或
#   shell 元字符时会断。用 remote_quote_args（printf %q）逐项转义后再拼。
#
# 【依赖】lib/core.sh、lib/remote.sh
# ============================================================================

REMOTE_SCRIPT="${ITP_KLOG_SCRIPT:-/home/java/k8s-logview.sh}"

# watcher 配置（旧值逐字保留；NS 首次开放 env 覆盖）
WATCH_NS="${KLOG_NS:-itp}"
WATCH_HISTORY="${KLOG_HISTORY:-1000}"
WATCH_STATE_FILE="${KLOG_WATCH_STATE:-/tmp/klog-watcher-state.txt}"

# 目标服务列表（旧 watcher 原样保留）
WATCH_SERVICES=(
  alipay-pay-sign-server
  alipay-account-server
  fep-alipay-server
  fep-alipay
  fep-app-server
  fep-app
  fep-dev-server
  ticket-server
  para-server
  blacklist-server
  pay-sign-server
  industry-data-server
  daily-ticket-server
  gate-txn-pay-server
  key-server
  collect-pay
  collect-pay-server
  account
  security-server
  es-server
)

# ---------------------------------------------------------------- 用法
log_usage() {
    cat <<EOF
itp-ops.sh log —— K8s 服务日志查看 / 重启自动重挂监控（经 ${REMOTE_HOST} 跳板）

用法: itp-ops.sh log <命令> [参数...]

命令
  tail <服务名|JAR前缀> [关键字...] [-f] [行数]
                          查看日志（默认最近 100 行）。参数顺序不敏感，
                          数字=行数，-f=跟踪，-2000f=历史 2000 行+跟踪，
                          其余按关键字（可多个，空格关键字加引号）。
  watch [--once]          监控守护：每 30s 巡检目标 Pod，重启/就绪态变化即
                          重挂 tail -f 监控（nohup 后台，Ctrl+C 退出 watcher
                          不会杀监控——这是特性，监控要的就是常驻）。
                          --once = 只跑一轮（调试用）。

示例
  itp-ops.sh log tail fep-app                  # 最近 100 行
  itp-ops.sh log tail fep-app 500              # 最近 500 行
  itp-ops.sh log tail fep-app ERROR            # 含 ERROR 的最近 100 行
  itp-ops.sh log tail fep-app -f               # ⭐ 实时跟踪（Ctrl+C 退出）
  itp-ops.sh log tail fep-app Exception -f     # ⭐ 实时跟踪含 Exception
  itp-ops.sh log tail fep-app "user login" -f  # ⭐ 带空格的关键字
  itp-ops.sh log tail fep-app -2000f           # ⭐ 最近 2000 行历史
  itp-ops.sh log tail fep-app ERROR Exception -2000f   # ⭐ 多关键字+历史
  KLOG_CONTAINER=nginx itp-ops.sh log tail gateway    # 指定多容器 Pod 的容器
  itp-ops.sh log watch                          # 启动监控守护

⚠️ 局限（与旧脚本一致，别当成失传）
  · 只看容器内日志文件的尾部窗口；Pod 一重启日志目录就消失。
    全量检索 MUST：ssh ${REMOTE_HOST} 后逐 Pod grep 日志文件
    （目录形如 /home/javaapp/app/logs/<POD_NAME>/<app>.log）。
  · 「klog 搜不到某行」≠「没发生」——重启即丢，见 docs/domain/decisions.md。
EOF
}

# ---------------------------------------------------------------- tail（原 klog.sh）
log_tail() {
    if [ $# -eq 0 ] || [[ "$1" == "-h" || "$1" == "--help" ]]; then
        log_usage
        return 0
    fi

    # printf %q 安全转义所有参数，NEVER 改成直接拼 "$@"
    local SAFE_ARGS
    SAFE_ARGS=$(remote_quote_args "$@")

    # KLOG_CONTAINER：ssh 不转发本地环境变量，显式拼成远端命令前缀才生效
    local PREFIX=""
    if [[ -n "${KLOG_CONTAINER:-}" ]]; then
        PREFIX="KLOG_CONTAINER=$(printf '%q' "$KLOG_CONTAINER") "
        log_info "指定容器: ${KLOG_CONTAINER}（经远端命令前缀生效）"
    fi

    # -t：强制伪终端，是 -f 跟踪 / Ctrl+C / 颜色 / 逐行刷新的前提，NEVER 去掉
    remote_exec_tty "${PREFIX}bash '${REMOTE_SCRIPT}' ${SAFE_ARGS}"
}

# ---------------------------------------------------------------- watch（原 klog-watcher.sh）
watch_get_pod() {
    remote_exec "kubectl get pods -n ${WATCH_NS} --no-headers -l app=${1} -o custom-columns='NAME:.metadata.name'" 2>/dev/null | head -1
}

watch_get_container_ready() {
    remote_exec "kubectl get pod -n ${WATCH_NS} ${1} -o jsonpath='{.status.containerStatuses[0].ready}'" 2>/dev/null
}

watch_get_restart_count() {
    remote_exec "kubectl get pod -n ${WATCH_NS} ${1} -o jsonpath='{.status.containerStatuses[0].restartCount}'" 2>/dev/null
}

# 杀进程树：先杀 ssh 子进程再杀 bash 壳，避免 remote tail -F 漏网累积
watch_kill_tree() {
    local pid="$1" child
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        watch_kill_tree "$child"
    done
    kill "$pid" 2>/dev/null || true
}

# 只杀自己启动过的监控（PID 记录 + cmdline 校验，防 PID 复用误杀、
# 也避免旧版 pgrep 模式把用户手工敲的 tail 一起杀掉）
watch_kill_monitor() {
    local svc="$1" pid_file="${WATCH_STATE_FILE}.d/${svc}.pid" pid args
    [[ -f "$pid_file" ]] || return 0
    pid=$(cat "$pid_file" 2>/dev/null)
    [[ -n "$pid" ]] || { rm -f "$pid_file"; return 0; }
    args=$(ps -p "$pid" -o args= 2>/dev/null)
    if [[ "$args" == *"log ${svc}"* ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] 终止旧监控: svc=${svc} pid=${pid}"
        watch_kill_tree "$pid"
        sleep 1
    fi
    rm -f "$pid_file"
}

watch_start_monitor() {
    local svc="$1" pod="$2"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 启动监控: svc=${svc} pod=${pod}"
    mkdir -p "${WATCH_STATE_FILE}.d"
    nohup bash "$ITP_OPS_ENTRY" log "$svc" "-${WATCH_HISTORY}f" >/dev/null 2>&1 &
    echo $! > "${WATCH_STATE_FILE}.d/${svc}.pid"
}

watch_pass() {
    local svc pod restart ready key old_key
    for svc in "${WATCH_SERVICES[@]}"; do
        pod=$(watch_get_pod "$svc")
        if [ -z "$pod" ]; then
            continue
        fi

        restart=$(watch_get_restart_count "$pod")
        ready=$(watch_get_container_ready "$pod")
        key="${svc}|${pod}|${restart}|${ready}"

        # 状态文件一行一条 `svc=key`，查找 MUST 按同一格式（旧版这里写错，见头注）
        old_key=$(grep "^${svc}=" "$WATCH_STATE_FILE" 2>/dev/null | tail -1 | cut -d'=' -f2-)

        if [ "$key" != "$old_key" ]; then
            # 状态变化：先杀旧监控，ready 再起新监控
            watch_kill_monitor "$svc"

            if [ "$ready" = "true" ]; then
                watch_start_monitor "$svc" "$pod"
            fi

            # 更新状态文件（grep -v 重建，避免 sed -i 的 BSD/GNU 差异）
            local tmp="${WATCH_STATE_FILE}.tmp"
            grep -v "^${svc}=" "$WATCH_STATE_FILE" > "$tmp" 2>/dev/null || true
            mv "$tmp" "$WATCH_STATE_FILE"
            echo "${svc}=${key}" >> "$WATCH_STATE_FILE"
        fi
    done
}

log_watch() {
    local ONCE=0
    while [ $# -gt 0 ]; do
        case "$1" in
            --once) ONCE=1; shift ;;
            -h|--help) log_usage; return 0 ;;
            *) echo "watch 未知参数：$1" >&2; return 1 ;;
        esac
    done

    mkdir -p "$(dirname "$WATCH_STATE_FILE")" "${WATCH_STATE_FILE}.d"
    > "$WATCH_STATE_FILE"

    echo "监控守护启动：ns=${WATCH_NS} history=${WATCH_HISTORY} 服务数=${#WATCH_SERVICES[@]}"
    echo "状态文件：${WATCH_STATE_FILE}（PID 记录：${WATCH_STATE_FILE}.d/）"
    [ "$ONCE" = "1" ] && echo "模式：--once（只跑一轮）"

    while true; do
        watch_pass
        [ "$ONCE" = "1" ] && break
        sleep 30
    done
}

# ---------------------------------------------------------------- 域入口
log_main() {
    need_cmd ssh
    local cmd="${1:-tail}"; shift || true
    case "$cmd" in
        tail|"")   log_tail "$@" ;;
        watch)     log_watch "$@" ;;
        help|-h|--help) log_usage; return 0 ;;
        *)         echo "未知命令：${cmd}" >&2; log_usage; return 1 ;;
    esac
}
