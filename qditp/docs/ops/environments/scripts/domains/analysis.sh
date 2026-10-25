#!/bin/bash
# ============================================================================
# itp-ops · analysis 域 —— 代码/文档静态分析（可重跑刷新文档数字）
#
# 【本文件是什么】
#   2026-10-25 重构：原 analysis/ 下 python 脚本的统一调度入口（当时三个）。
#   脚本本身一行未改（是可重跑的生成器/计数器），本域只做三件事：
#     1. cd 到仓库根再执行 —— 脚本 historically 按「从仓库根运行」
#        设计（相对路径读写 docs/），从别处跑会静默
#        找不到文件或把输出写到错地方；
#     2. 命令名稳定（endpoints），不再记文件名；
#     3. 统一 usage / 依赖检查。
#
#   2026-10-26 合理性审计：extract_interfaces.py（依赖不存在的 API.md /
#   docs/提取的需求，一跑即崩）与 extract_requirements.py（源目录全不存在，
#   实跑空转输出 0 文档）已删除，interfaces/requirements 两个子命令同步下线。
#   【为什么只剩一个】保留判据 = 能跑 + 有人用：endpoints 是文档里
#   「端点计数」数字的可重跑真源；那两个从来没有可跑的源文档。
#
# 【依赖】lib/core.sh；python3（脚本本身只依赖标准库）
# ============================================================================

analysis_usage() {
    cat <<EOF
itp-ops.sh analysis —— 代码/文档静态分析（从仓库根执行，输出写回 docs/）

用法: itp-ops.sh analysis <命令> [参数...]

命令
  endpoints <模块源码目录|.> [--csv]
                          方法级 HTTP 端点数统计（@Get/Post/Put/Delete/RequestMapping，
                          仅 *Controller.java，类级 @RequestMapping 不计）。
                          传 . = 全仓。--csv 额外输出 CSV。
                          典型：itp-ops.sh analysis endpoints account-server/src/main/java
  help                     本帮助

说明
  · 脚本在仓库根 cwd 下运行（相对路径读写在 docs/），
    本域自动 cd，NEVER 从子目录直接 python3 调它们。
  · 这是轻量静态分析，不解析条件编译/多 profile；数字随代码变动，
    改动接口后 MUST 重跑刷新文档。
EOF
}

analysis_run() {
    local script="$1"; shift
    cd "$ITP_OPS_ROOT" || die "无法 cd 到仓库根：${ITP_OPS_ROOT}"
    python3 "${ITP_OPS_DIR}/analysis/${script}" "$@"
}

analysis_main() {
    need_cmd python3
    local cmd="${1:-}"; shift || true
    case "$cmd" in
        ""|help|-h|--help) analysis_usage; return 0 ;;
        endpoints)   analysis_run count-endpoints.py "$@" ;;
        *)           echo "未知命令：${cmd}" >&2; analysis_usage; return 1 ;;
    esac
}
