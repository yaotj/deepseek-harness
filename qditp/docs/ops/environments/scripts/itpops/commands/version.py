"""version 域 —— 升级版本号计算（本地计算，不依赖环境/远端）。

与旧 itp-ops.sh 的 version_main 逐语义对齐（help 文案、stdout 字节兼容）。
"""
from __future__ import annotations

from ..core import ui
from ..core.version import debug_version, next_version
from ..errors import EXIT_USAGE, OpsError

CATALOG = {
    "name": "version",
    "summary": "升级版本号计算（前端/后端统一规则）",
    "default_env": None,
    "env_note": "环境无关（本地计算，不打远端）",
    "subcommands": [
        {
            "name": "next",
            "usage": "version next <当前版本>",
            "summary": "末位 +1；任一分段 >99 向高位进位（1.2.99 → 1.3.0；99.99 → 1.0.0）",
            "args": [{"name": "当前版本", "required": True}],
            "writes": False,
            "example": "itp-ops.sh version next 1.2.42",
            "stdout_human": "1.2.43",
            "json_data": {"current": "1.2.42", "next": "1.2.43"},
        },
        {
            "name": "debug",
            "usage": "version debug",
            "summary": "打印 CloudOS 调试包哨兵版本 0.0.0.1（不占用发布序列）",
            "args": [],
            "writes": False,
            "example": "itp-ops.sh version debug",
            "stdout_human": "0.0.0.1",
            "json_data": {"version": "0.0.0.1"},
        },
    ],
}

_HELP = """\
itp-ops version —— 升级版本号规则（前端/后端统一，2026-11-02 裁定）
用法:
  itp-ops.sh version next <当前版本>   # 末位 +1；任一分段 >99 向高位进位（1.2.99 → 1.3.0）
  itp-ops.sh version debug            # 打印 CloudOS 调试包哨兵版本 0.0.0.1"""


def main(args: list[str]) -> int:
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_HELP)
        return 0
    if sub == "next":
        if not rest:
            raise OpsError("version next 需要当前版本号", EXIT_USAGE)
        current = rest[0]
        nxt = next_version(current)
        ui.result({"current": current, "next": nxt})
        ui.out(nxt)  # stdout 与 bash 版字节兼容：只打印下一版本
        return 0
    if sub == "debug":
        v = debug_version()
        ui.result({"version": v})
        ui.out(v)
        return 0

    raise OpsError(f"未知子命令：{sub}（支持 next <version> / debug）", EXIT_USAGE)
