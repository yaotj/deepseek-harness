"""sentinel 域 —— route-sentinel 首次部署（清理残留 → 上传 → 构建 → 部署）。

【命名（2026-11-03 review）】
  旧入口 `itp-ops.sh sentinel` 名字长且带 hongdao；现收敛为
  `itp-ops.sh sentinel`，实现在 `itpops/sentinel/core.py`。
"""
from __future__ import annotations

from .. import sentinel as _impl
from ..core import ui

CATALOG = dict(_impl.CATALOG)


def main(args: list[str]) -> int:
    ctx = ui.context()
    if args and args[0] in ("help", "-h", "--help"):
        return _impl.main(["-h"])
    argv = list(args)
    # 仅当 <jar-path> <dockerfile-path> 齐了才回注 --env，避免挤占位置参数槽位
    positionals = [a for a in argv if not a.startswith("-")]
    if len(positionals) >= 2 and ctx.env \
            and "--env" not in argv and not any(a.startswith("--env=") for a in argv):
        argv += ["--env", ctx.env]
    if ctx.yes and "--yes" not in argv:
        argv.append("--yes")
    return _impl.main(argv)
