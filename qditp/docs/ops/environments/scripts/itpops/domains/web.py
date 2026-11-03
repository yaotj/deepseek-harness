"""web 域 —— 前端发版 / web-admin 导出目录挂载。

【命名（2026-11-03 review）】
  原两个独立入口 `itp-ops.sh web release` 与 `itp-ops.sh web mount-export`
  收敛为 `itp-ops.sh web release` / `itp-ops.sh web mount-export`，
  实现留在 `itpops/web/`（纯 Python，仍可 python3 -m 直调）。
"""
from __future__ import annotations

from ..core import ui
from ..errors import EXIT_USAGE, OpsError
from ..web import frontend_release as _release
from ..web import mount_export as _mount

CATALOG = {
    "name": "web",
    "summary": "web 前端发版（CloudOS 应用包）与 web-admin 导出目录挂载",
    "default_env": None,
    "env_note": "release 走 CloudOS 双平台（--env/CLOUDOS_ENV，默认 hongdao）；mount-export 固定测试环境",
    "subcommands": [_release.CATALOG, _mount.CATALOG],
}

_HELP = """\
itp-ops web —— web 前端发版与挂载

用法:
  itp-ops.sh web release <版本|--bump|--debug> [--yes] [--skip-build] [--skip-upgrade] [--app …]
  itp-ops.sh web mount-export [--yes]

子命令:
  release       出 zip → 上传软件中心 → 升级 CloudOS 应用 → 核验（缺 --yes 为 dry-run）
  mount-export  web-admin 挂 app-logs-v2 到 /home/javaapp/app/export（缺 --yes 为预演）

单命令帮助：itp-ops.sh web release -h / itp-ops.sh web mount-export -h"""


def _inject(args: list[str]) -> list[str]:
    ctx = ui.context()
    argv = list(args)
    if ctx.env and "--env" not in argv and not any(a.startswith("--env=") for a in argv):
        argv += ["--env", ctx.env]
    if ctx.yes and "--yes" not in argv:
        argv.append("--yes")
    return argv


def main(args: list[str]) -> int:
    sub = args[0] if args else "help"
    rest = args[1:]
    if sub in ("help", "-h", "--help"):
        ui.out(_HELP)
        return 0
    if sub == "release":
        return _release.main(_inject(rest))
    if sub == "mount-export":
        return _mount.main(_inject(rest))
    raise OpsError(f"未知子命令：{sub}（支持 release / mount-export）", EXIT_USAGE)
