"""buildx 域 —— 镜像构建（CloudOS buildx 端点，双环境）。

【命名（2026-11-03 review）】
  旧入口 `itp-ops.sh buildx` 只在红岛首发时叫这个名字，实际 `--env hongdao|test`
  两套环境都用，名字有误导；现收敛为唯一入口的子命令 `itp-ops.sh buildx`，
  实现在 `itpops/buildx.py`（纯 Python，仍可 `itp-ops.sh buildx` 直调）。
"""
from __future__ import annotations

from .. import buildx as _impl
from ..core import ui

CATALOG = dict(_impl.CATALOG)


def main(args: list[str]) -> int:
    ctx = ui.context()
    if args and args[0] in ("help", "-h", "--help"):
        return _impl.main(["-h"])  # 实现自带用法文本（stderr）
    argv = list(args)
    # 只有凑齐 <module> <version> 才回注 --env——实现要求前两个位置参数是它们，
    # 空参/缺参时注入会把 --env 挤进位置参数槽位（历史 bug）。
    positionals = [a for a in argv if not a.startswith("-")]
    if len(positionals) >= 2 and ctx.env \
            and "--env" not in argv and not any(a.startswith("--env=") for a in argv):
        argv += ["--env", ctx.env]
    return _impl.main(argv)
