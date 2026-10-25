#!/bin/bash
# ============================================================================
# itp-ops 公共骨架 —— 日志 / 写操作门 / 仓库根 / 依赖检查
#
# 【纪律（与 lib/cloudos-* 一致，MUST 保持）】
#   · lib NEVER 设 shell 选项（set -euo），由入口 itp-ops.sh 决定
#   · 函数失败返回非 0 + 调用方显式判；NEVER 在命令替换里 die
#     （子 shell 的 exit 传不出来，父 shell 会拿空值继续，表现为一串下游错误）
#   · 写操作默认拒绝，必须显式 --yes（与旧脚本逐条对齐，不新增、不削弱）
#
# 【全局参数（入口解析后设为全局变量，各域读）】
#   OPT_ENV   环境名，仅 cloudos 域使用（hongdao 默认 / test）
#   OPT_YES   1 = 本次已确认写操作
#   OPT_DRY   1 = 干跑（预留；现有 dry-run 语义在各子命令内，未启用全局开关）
# ============================================================================

ITP_OPS_VERSION="1.0.0"

# ---- 颜色：仅 TTY 上色，重定向/管道时纯文本（便于 grep 与日志归档）----
if [[ -t 1 ]]; then
    C_GREEN='\033[0;32m'; C_RED='\033[0;31m'; C_YELLOW='\033[1;33m'
    C_CYAN='\033[0;36m'; C_BLUE='\033[0;34m'; C_NC='\033[0m'
else
    C_GREEN=''; C_RED=''; C_YELLOW=''; C_CYAN=''; C_BLUE=''; C_NC=''
fi

log_info()  { printf "${C_GREEN}[INFO]${C_NC} %s\n"  "$*"; }
log_warn()  { printf "${C_YELLOW}[WARN]${C_NC} %s\n" "$*" >&2; }
log_error() { printf "${C_RED}[ERROR]${C_NC} %s\n"  "$*" >&2; }
log_step()  { printf "${C_CYAN}[STEP]${C_NC} %s\n"  "$*"; }
die()       { printf "${C_RED}错误：${C_NC}%s\n" "$*" >&2; exit 1; }

# ---- 写操作门：缺 --yes 即拒（生产脚本的既有行为，逐条保留）----
require_yes() {
    if [[ "${OPT_YES:-0}" != "1" ]]; then
        die "这是写操作：$1。确认无误请加 --yes"
    fi
}

# ---- 外部依赖检查（入口启动时统一预检，缺什么一次说清）----
need_cmd() {
    local missing=() c
    for c in "$@"; do
        command -v "$c" >/dev/null 2>&1 || missing+=("$c")
    done
    [[ ${#missing[@]} -eq 0 ]] || die "缺少命令：${missing[*]}"
}

# ---- 仓库根：本文件在 <repo>/docs/ops/environments/scripts/lib/，根在 5 层之上 ----
# cd 的 .. 可无限叠加，深度写错不会报错，故 MUST 用标志目录断言。
ITP_OPS_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ITP_OPS_DIR="$(cd "${ITP_OPS_LIB_DIR}/.." && pwd)"
ITP_OPS_ROOT="$(cd "${ITP_OPS_LIB_DIR}/../../../../.." && pwd)"
[[ -d "${ITP_OPS_ROOT}/account-server" ]] \
    || die "仓库根推导错（${ITP_OPS_ROOT}），itp-ops 可能被移动了位置"
ITP_OPS_ENTRY="${ITP_OPS_DIR}/itp-ops.sh"
