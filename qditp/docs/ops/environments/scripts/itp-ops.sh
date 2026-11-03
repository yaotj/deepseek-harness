#!/bin/bash
# ============================================================================
# itp-ops —— 青岛地铁 ITP 平台运维单一入口（薄壳）
#
# 【结构】唯一入口 = 本文件；实现全在 Python 包 itpops/（纯标准库，分层对标 pip）
#
#   itp-ops.sh            本文件：薄壳，exec python3 -m itpops
#   pyproject.toml        包元数据 + `itp-ops` console script（pipx 可装）+ ruff/pytest 配置
#   itpops/
#     cli/                main.py（参数解析 + 域分发 + JSON 信封）| registry.py（域清单）
#                         help.py（分层帮助）| completion.py（bash/zsh 补全）
#     commands/           一个文件 = 一个命令域：cloudos buildx k8s log diag web
#                         sentinel analysis version catalog
#     core/               ui | version | environments | remote | runtime
#     cloudos/ buildx/ sentinel/ web/   特性实现（commands/ 只做编排）
#   tests/                stdlib unittest（版本 / 环境 / CLI 契约 / 注册表）
#
# 【命令面】--env/-e、--yes/-y、--dry-run/-n、-o json、-q、-v、--no-color、--trace、
#   -h、-V、--、<域> help、<域> <命令> -h、catalog、completion bash|zsh
#
# 【双受众契约】
#   人：结果 → stdout，日志/横幅 → stderr，TTY 才着色
#   大模型：-o json → stdout 单条 {ok, command, data, error, duration_ms}；退出码稳定
#   退出码：0 成功 / 2 用法 / 3 前置 / 4 传输 / 5 执行 / 6 缺 --yes / 130 中断
# ============================================================================
set -u

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# python3 -m itpops 需要把 scripts 目录放进模块搜索路径
export PYTHONPATH="${ITP_OPS_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
exec python3 -m itpops "$@"
