#!/bin/bash
# ============================================================================
# itp-ops —— 青岛地铁 ITP 平台运维单一入口（薄壳）
#
# 【结构】唯一入口 = 本文件；实现全在 Python 包 itpops/（纯标准库）
#   README.md / CONTRIBUTING.md / CHANGELOG.md / docs/cloudos-notes.md   项目文档
#   pyproject.toml / .gitignore / itpops/py.typed                         项目元数据
#   itpops/cli/        表现层：main / registry / help / completion
#   itpops/commands/   命令层：一域一文件（CATALOG + main，只做注册与分发）
#   itpops/core/       公共支撑：ui / version / environments / remote / runtime / crypto
#   itpops/workflows/  工作流执行器 + backend / frontend / jvm 声明
#   itpops/<特性>/     实现：cloudos(common/apps/scaling/deploy/images/impl) /
#                      analysis / versioning / linting
#   tests/             stdlib unittest（136 例）
#   hosts/ migration/ buildx.Dockerfile   仓内资产（远端脚本 / 迁移执行包 / 构建模板）
#
# 【命令面】--env/-e、--yes/-y、--dry-run/-n、-o json、-q、-v、--no-color、--trace、
#   -h、-V、--、<域> help、<域> <命令> -h、catalog、completion bash|zsh、lint yaml
#   flow list | flow backend <模块> --yes | flow frontend --yes | flow jvm <应用> --yes
#   flow guide（命令模板）/ flow guide -i（交互问答，只生成命令不执行）
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
