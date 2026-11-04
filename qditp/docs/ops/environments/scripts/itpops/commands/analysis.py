"""itpops · analysis 域 —— 代码静态分析（可重跑刷新文档数字）。

【2026-11-04 收编】原 `analysis/count-endpoints.py` 已并入 `itpops/analysis/endpoints.py`：
不再起子进程、不再要求「先 cd 到仓库根」，相对路径统一按仓库根解析。
输出文本与旧脚本逐字节一致；`--csv` 仍按调用方给的前缀拼路径（`.` → `./x`）。

【与 bash 版的差异】
  1. 失败退出码细分：用法 2 / 目录不存在 3 / 其它 5（bash 原样透传 python3 退出码）。
  2. 目录不存在时明确报错（旧脚本 os.walk 空转、静默输出「合计 0」，容易误判为「没有端点」）。
  3. 错误行前缀统一「错误：」。
"""
from __future__ import annotations

from pathlib import Path

from ..analysis import endpoints
from ..core import ui
from ..core.runtime import repo_root
from ..errors import EXIT_PRECONDITION, EXIT_USAGE, OpsError

_HELP = """\
itp-ops analysis —— 代码静态分析（本机文件系统，环境无关）

用法: itp-ops.sh analysis <命令> [参数...]

命令
  endpoints <模块源码目录|.> [--csv]
                          方法级 HTTP 端点数统计（@Get/Post/Put/Delete/RequestMapping，
                          仅 *Controller.java，类级 @RequestMapping 不计）。
                          传 . = 全仓。--csv 额外输出 CSV。
                          典型：itp-ops.sh analysis endpoints account-server/src/main/java
  help                     本帮助

说明
  · 计数实现在 itpops/analysis/endpoints.py（原 analysis/count-endpoints.py）。
  · 相对路径以仓库根为基准，NEVER 需要先 cd。
  · 这是轻量静态分析，不解析条件编译/多 profile；数字随代码变动，
    改动接口后 MUST 重跑刷新文档。"""


def _endpoints(rest: list[str]) -> int:
    csv = "--csv" in rest
    unknown = [a for a in rest if a.startswith("-") and a != "--csv"]
    if unknown:
        raise OpsError(f"未知参数：{unknown[0]}（用法：analysis endpoints <目录> [--csv]）",
                       EXIT_USAGE)
    positional = [a for a in rest if not a.startswith("-")]
    if not positional:
        raise OpsError("analysis endpoints 需要目录参数（模块 src/main/java 或 .）", EXIT_USAGE)
    if len(positional) > 1:
        raise OpsError("analysis endpoints 只接受一个目录参数", EXIT_USAGE)

    root_arg = positional[0]
    root = Path(root_arg)
    scan_root = root if root.is_absolute() else repo_root() / root
    if not scan_root.is_dir():
        raise OpsError(f"目录不存在：{root_arg}", EXIT_PRECONDITION)

    rows = endpoints.scan(scan_root)
    for line in endpoints.render(rows, root_arg, csv=csv):
        ui.out(line)
    ui.result({"root": root_arg, "csv": csv,
               "total": sum(r[1] for r in rows), "files": len(rows)})
    return 0


# ---------------------------------------------------------------- 域入口
CATALOG = {
    "name": "analysis",
    "summary": "代码静态分析：方法级 HTTP 端点数统计（可重跑刷新文档数字）",
    "default_env": None,
    "env_note": "环境无关（本机静态分析，不调 envx、不打远端）",
    "subcommands": [
        {
            "name": "endpoints",
            "usage": "analysis endpoints <模块源码目录|.> [--csv]",
            "summary": "方法级 HTTP 端点数统计（@Get/Post/Put/Delete/RequestMapping，仅 *Controller.java，类级 @RequestMapping 不计）。传 . = 全仓；--csv 额外输出 CSV",
            "args": [{"name": "模块源码目录|.", "required": True}, {"name": "--csv", "required": False}],
            "writes": False,
            "example": "itp-ops.sh analysis endpoints account-server/src/main/java",
            "stdout_human": " 12  XxxController.java（含类级 1）\n…\n方法级端点合计: 42  （文件数 7）",
            "json_data": {"root": "account-server/src/main/java", "csv": False, "total": 42, "files": 7},
        },
    ],
}


def main(args: list[str]) -> int:
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_HELP)
        return 0
    if sub == "endpoints":
        return _endpoints(rest)

    ui.out(_HELP)
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)
