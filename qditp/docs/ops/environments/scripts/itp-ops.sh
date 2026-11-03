#!/bin/bash
# ============================================================================
# itp-ops v2 —— 青岛地铁 ITP 平台运维单一入口（薄壳）
#
# 【结构】Python 底层 + 分模块（2026-11-03 重构定稿，双受众：人 + 大模型）
#
#   itp-ops.sh                本文件：薄壳，转调 python3 -m itpops
#   itpops/                   Python 包（底层实现，纯标准库）
#     cli.py                  全局参数解析 + 域分发（--json 信封契约）
#     catalog.py              自描述命令注册表（大模型入口：catalog --json）
#     core/                   ui / version / envx / remote / execx
#     domains/                cloudos / k8s / log / diag / analysis / version
#   itp-ops.legacy.sh         v1 bash 分发器（迁移期新旧输出对照基线，迁移完成后删）
#
# 【命令面】与 v1 逐字兼容：--env hongdao|test、--yes、-h/--help、--version、
#   域与子命令、stdout 结果文本不变。新增：--json（机器信封）、catalog。
#
# 【双受众契约】
#   人：结果 → stdout，日志/横幅 → stderr，TTY 才着色
#   大模型：--json → stdout 单条 {ok, command, data, error}；catalog --json 自描述
#   退出码：0 成功 / 2 用法 / 3 前置 / 4 传输 / 5 执行 / 6 缺 --yes / 1 其它
# ============================================================================
set -u

ITP_OPS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# python3 -m itpops 需要把 scripts 目录放进模块搜索路径
export PYTHONPATH="${ITP_OPS_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
exec python3 -m itpops "$@"
