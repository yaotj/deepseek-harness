#!/bin/bash
# ============================================================================
# itp-ops —— 青岛地铁 ITP 平台运维单一入口
#
# 【结构】单一入口 + 公共 lib + 按域分组（2026-10-25 重构定稿）
#
#   itp-ops.sh                本文件：全局参数解析 + 域分发
#   lib/core.sh               日志 / 写操作门(--yes) / 仓库根 / 依赖检查
#   lib/remote.sh             ssh 跳板助手（按 --env 选 k8s-master / hd-linux；TTY 纪律）
#   lib/cloudos-*.sh          CloudOS 平台 API（auth/http/image/pkg）
#   domains/cloudos.sh        云管台运维（apps/env/pods/scale/logs/image…）
#   domains/k8s.sh            镜像构建推送（deploy / deploy-all）
#   domains/log.sh            日志查看 / 重启自动重挂监控（tail / watch）
#   domains/diag.sh           可达性自检 / 入向路由导出（reach / routes）
#   domains/analysis.sh       静态分析（endpoints / interfaces / requirements）
#
# 【全局参数（可出现在任意位置，入口负责剥离，域只收到业务参数）】
#   --env hongdao|test        环境切换（全局，所有域生效）；各域默认值不统一
#                             （cloudos=hongdao；k8s/log=test；diag 按子命令），
#                             未指定时用域自身默认
#   --yes                     确认写操作。缺它，写命令一律拒绝（与旧脚本对齐）
#   -h / --help / --version   仅域前有效；域后的 -h 交给域自己解释
#
# 【lib 纪律（MUST 与 lib/ 内注释一致）】
#   · lib NEVER 设 shell 选项；入口设 set -uo pipefail（刻意不用 -e：
#     失败显式判，不靠 Shell 兜底）
#   · 函数失败返回非 0 + 调用方显式判；NEVER 在命令替换里 die
#
# 【bash 3.2 兼容（macOS 本机 /bin/bash 是 3.2.57）】
#   · NEVER 用 declare -A / [[ -v arr[k] ]] / mapfile
#   · 空数组展开 MUST 写 ${arr[@]+"${arr[@]}"}（3.2 下 set -u 会误报未绑定）
# ============================================================================

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

set -uo pipefail

# ---------------------------------------------------------------- 加载 lib 与域
# core.sh 必须先来（die / log_* / ITP_OPS_ROOT 断言都靠它）
# shellcheck source=lib/core.sh
source "${ITP_OPS_DIR}/lib/core.sh"
# shellcheck source=lib/remote.sh
source "${ITP_OPS_DIR}/lib/remote.sh"
# shellcheck source=lib/env.sh
source "${ITP_OPS_DIR}/lib/env.sh"
for _lib in cloudos-auth cloudos-http cloudos-image cloudos-pkg; do
    # shellcheck source=/dev/null
    source "${ITP_OPS_DIR}/lib/${_lib}.sh"
done
for _domain in "${ITP_OPS_DIR}"/domains/*.sh; do
    [ -e "$_domain" ] || continue
    # shellcheck source=/dev/null
    source "$_domain"
done
unset _lib _domain

# ---------------------------------------------------------------- 总用法
itp_usage() {
    cat <<EOF
itp-ops v${ITP_OPS_VERSION} —— 青岛地铁 ITP 平台运维单一入口

用法: itp-ops.sh [全局参数...] <域> [域参数...]
      itp-ops.sh help

全局参数（可出现在任意位置）
  --env hongdao|test   环境切换（全局，所有域生效）。未指定时用各域自己的默认：
                       cloudos=hongdao、k8s/log=test、diag 按子命令（reach=hongdao、routes=test）
                       默认值刻意不统一——NEVER 擅自改齐，那会让原可用的命令失效；
                       每个域运行前都会打印「[环境]」横幅，明确当前打的是哪个环境
  --yes                确认写操作；缺它写命令一律拒绝
  -h | --help          总帮助（仅域前）
  --version           版本号（仅域前）

域一览
  cloudos    云管台运维：应用列表/env/实例/日志/副本伸缩/镜像构建…
            itp-ops.sh [--env hongdao|test] cloudos <命令> [参数]
            itp-ops.sh cloudos health            # 连通性自检，写操作前必跑
            itp-ops.sh cloudos apps Error        # 只列异常态应用
            itp-ops.sh cloudos env fep-app       # env 全量（排 RPC 地址错配首选）
            itp-ops.sh cloudos scale fep-app 2 --yes
  k8s       镜像构建推送（本机 jar → k8s-master → Harbor；**只对测试环境可用**，
            红岛发版走 cloudos image，见 domains/k8s.sh 头注）
            itp-ops.sh k8s deploy fep-dev-server --yes
            itp-ops.sh k8s deploy-all --yes      # 有序全量，失败即断
  log       日志查看 / 重启自动重挂监控
            itp-ops.sh log tail fep-app -f       # 实时跟踪
            itp-ops.sh log watch                 # 监控守护（Pod 重启自动重挂）
  diag      网络可达性自检 / 入向路由导出
            itp-ops.sh diag reach                # 红岛分区可达性
            itp-ops.sh diag routes               # 48000 入向路由全链路导出
  analysis  静态分析（端点计数 / 接口清单 / 需求提取）
            itp-ops.sh analysis endpoints account-server/src/main/java

每个域的详细用法：itp-ops.sh <域> help
仓库根：${ITP_OPS_ROOT}
EOF
}

# ---------------------------------------------------------------- 全局参数扫描
OPT_YES=0
# --env 全局化：写入 ITP_ENV，由 lib/env.sh 的 itp_env_init 消费。
# 未指定 --env 时不在此设默认——各域按自身可用性传默认（见 lib/env.sh 头注）。
# CLOUDOS_ENV 保留为兼容别名（cloudos 域历史变量名）。
DOMAIN=""
DOMAIN_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)
            [[ $# -ge 2 ]] || die "--env 缺值（hongdao|test）"
            ITP_ENV="$2"; ITP_ENV_EXPLICIT=1
            CLOUDOS_ENV="$2"      # 兼容别名：cloudos 域历史变量名
            shift 2 ;;
        --env=*)
            ITP_ENV="${1#--env=}"; ITP_ENV_EXPLICIT=1
            CLOUDOS_ENV="${ITP_ENV}"   # 兼容别名
            shift ;;
        --yes)
            OPT_YES=1; shift ;;
        -h|--help)
            if [[ -z "$DOMAIN" ]]; then itp_usage; exit 0; fi
            DOMAIN_ARGS+=("$1"); shift ;;
        --version)
            if [[ -z "$DOMAIN" ]]; then
                echo "itp-ops ${ITP_OPS_VERSION}"
                exit 0
            fi
            DOMAIN_ARGS+=("$1"); shift ;;
        *)
            if [[ -z "$DOMAIN" ]]; then
                DOMAIN="$1"
            else
                DOMAIN_ARGS+=("$1")
            fi
            shift ;;
    esac
done

# ---------------------------------------------------------------- 域分发
case "$DOMAIN" in
    "")
        itp_usage
        exit 0 ;;
    help)
        itp_usage
        exit 0 ;;
    cloudos)
        cloudos_main ${DOMAIN_ARGS[@]+"${DOMAIN_ARGS[@]}"} ;;
    k8s)
        k8s_main ${DOMAIN_ARGS[@]+"${DOMAIN_ARGS[@]}"} ;;
    log)
        log_main ${DOMAIN_ARGS[@]+"${DOMAIN_ARGS[@]}"} ;;
    diag)
        diag_main ${DOMAIN_ARGS[@]+"${DOMAIN_ARGS[@]}"} ;;
    analysis)
        analysis_main ${DOMAIN_ARGS[@]+"${DOMAIN_ARGS[@]}"} ;;
    *)
        echo "未知域：${DOMAIN}" >&2
        itp_usage
        exit 1 ;;
esac
