#!/bin/bash
# ============================================================================
# route-sentinel → 红岛 itpb 首次部署（薄壳，命令面不变）
#
# 实现已移植至 Python：itpops/route_sentinel.py（python3 -m itpops.route_sentinel）。
# 旧 bash 全文见 hongdao-deploy-route-sentinel.legacy.sh（迁移期对照基线，迁移完成后删）。
# 用法: hongdao-deploy-route-sentinel.sh <jar-path> <dockerfile-path> [--env hongdao|test] [--yes]
# 二次确认口径（AGENTS.md §5.C 生产红线）：写操作必须带 --yes（缺它本地即拒）。
# ============================================================================
set -u

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# python3 -m itpops.route_sentinel 需要把 scripts 目录放进模块搜索路径
export PYTHONPATH="${ITP_OPS_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
exec python3 -m itpops.route_sentinel "$@"
