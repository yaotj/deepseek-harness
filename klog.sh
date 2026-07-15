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
# 使用 printf %q 安全转义所有参数
# 防止特殊字符(引号/空格/$等)在 SSH 传输时被远程 Shell 二次解析
SAFE_ARGS=$(printf '%q ' "$@")

# -t : 强制分配伪终端(TTY)
#      这是实时跟踪(-f)正常工作的关键:
#      1. tail -f 需要 TTY 才能正确响应 Ctrl+C
#      2. 远程脚本的颜色输出(\033[xxm)需要 TTY 才能渲染
#      3. grep --line-buffered 需要 TTY 才能实现逐行刷新
ssh -t "$REMOTE_HOST" "bash '$REMOTE_SCRIPT' $SAFE_ARGS"
