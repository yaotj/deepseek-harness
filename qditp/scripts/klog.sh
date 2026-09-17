#!/bin/bash
# ==========================================
# Mac 端 K8s 日志快捷查看代理脚本
# ==========================================

REMOTE_HOST="k8s-master"
REMOTE_SCRIPT="/home/java/k8s-logview.sh"
# ---------- 帮助信息 (包含多种传参示例) ----------
show_help() {
    cat <<EOF
🚀 K8s 日志远程快捷查看工具

用法: $0 <服务名或JAR前缀> [关键字] [-f] [行数]

📌 传参示例:
  $0 fep-app                      # 查看最近 100 行日志
  $0 fep-app 500                  # 查看最近 500 行日志
  $0 fep-app ERROR                # 搜索包含 ERROR 的最近 100 行
  $0 fep-app ERROR 200            # 搜索包含 ERROR 的最近 200 行
  $0 fep-app -f                   # ⭐ 实时跟踪日志 (Ctrl+C 退出)
  $0 fep-app Exception -f         # ⭐ 实时跟踪包含 Exception 的日志
  $0 fep-app "user login" -f      # ⭐ 实时跟踪带空格的关键字
  $0 fep-app -2000f               # ⭐ 查看最近 2000 行历史日志
  $0 fep-app ERROR Exception -2000f # ⭐ 多关键字过滤 + 历史日志
  KLOG_CONTAINER=nginx $0 gateway # 指定多容器 Pod 中的特定容器

💡 提示:
  - 参数顺序不敏感，脚本会自动识别数字、-f 和关键字
  - 实时跟踪模式下按 Ctrl+C 安全退出
EOF
    exit 0
}

# 无参数或请求帮助时显示用法
if [ $# -eq 0 ] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
    show_help
fi

# ---------- 核心执行逻辑 ----------
# 使用 printf %q 安全转义所有参数。NEVER 改成直接拼 "$@"（详见 docs/ops/生产环境清单.md 附.二.1）
SAFE_ARGS=$(printf '%q ' "$@")

# -t : 强制分配伪终端(TTY)，是 -f 跟踪 / Ctrl+C / 颜色 / 逐行刷新的前提。NEVER 去掉
ssh -t "$REMOTE_HOST" "bash '$REMOTE_SCRIPT' $SAFE_ARGS"
