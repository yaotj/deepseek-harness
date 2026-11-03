#!/bin/bash
# ============================================================================
# hongdao-buildx —— 红岛 CloudOS 镜像构建（薄壳，命令面不变）
#
# 实现已移植至 Python：itpops/buildx.py（python3 -m itpops.buildx）。
# 旧 bash 全文见 hongdao-buildx.legacy.sh（迁移期对照基线，迁移完成后删）。
# 用法: hongdao-buildx.sh <module> <version> [--env hongdao|test] [--jar <jar>] \
#       [--dockerfile <df>] [--project <name>] [--img-type <uuid>] [--base-image <ref>] [--check]
# ============================================================================
set -u

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# python3 -m itpops.buildx 需要把 scripts 目录放进模块搜索路径
export PYTHONPATH="${ITP_OPS_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
exec python3 -m itpops.buildx "$@"
