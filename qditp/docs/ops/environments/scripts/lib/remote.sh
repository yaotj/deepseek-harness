#!/bin/bash
# ============================================================================
# 远端执行助手 —— 跳板机按环境选（test=k8s-master / hongdao=hd-linux），本机不直连集群
#
# 【为什么走 ssh 而不是本机 kubectl】
#   本机 kubeconfig 没有 ITP 集群凭据（见 AGENTS.md §7），所有集群操作 MUST
#   经跳板：ssh <REMOTE_HOST> "kubectl ..."。REMOTE_HOST 由 lib/env.sh 按 --env 设定。
#
# 【TTY 与否（MUST 分清，别合并）】
#   remote_exec      非交互：一次性命令、抓输出。BatchMode 友好。
#   remote_exec_tty  强制伪终端：-f 实时跟踪 / Ctrl+C / 颜色 / 逐行刷新。
#                    NEVER 给跟踪命令去掉 -t，否则不刷新且 Ctrl+C 杀不掉对端。
#
# 【参数转义】
#   远端命令是拼字符串执行的，NEVER 直接拼 "$@"——服务名/关键字含空格或shell
#   元字符时会断。用 remote_quote_args（printf %q）逐项转义后再拼。
#
# 依赖：lib/core.sh（die）
# ============================================================================

REMOTE_HOST="${ITP_REMOTE_HOST:-k8s-master}"

# 非交互执行；$1 为完整远端命令串
remote_exec() {
    ssh -o ConnectTimeout=8 "$REMOTE_HOST" "$1"
}

# 强制 TTY 执行（跟踪/交互场景）
remote_exec_tty() {
    ssh -t -o ConnectTimeout=8 "$REMOTE_HOST" "$1"
}

# 连通性预检（BatchMode，不弹密码提示——不通就是不通，快速失败）
remote_reachable() {
    ssh -o ConnectTimeout=6 -o BatchMode=yes "$REMOTE_HOST" true 2>/dev/null
}

# 把本地参数安全转义成可拼进远端命令串的形式
remote_quote_args() {
    printf '%q ' "$@"
}
