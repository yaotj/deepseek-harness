"""catalog 命令 —— 全量命令注册表（人看文本，大模型 -o json）。

数据来源：`cli/registry.py`（域清单 + CATALOG 汇总）。本模块只做渲染。
"""
from __future__ import annotations

from ..cli.registry import build_catalog
from ..core import ui

CATALOG = {
    "name": "catalog",
    "summary": "自描述命令注册表（域/子命令/参数/写操作/退出码），大模型入口",
    "default_env": None,
    "subcommands": [],
    "usage": "catalog [-o json]",
    "args": [{"name": "-o, --output json", "desc": "输出机器可读注册表"}],
    "writes": False,
    "example": "itp-ops.sh catalog -o json | jq '.data.domains[].name'",
}


def render(cat: dict) -> str:
    lines = [
        f"itp-ops catalog v{cat['version']} —— 全量命令注册表（机器可读：加 -o json）",
        "",
        "全局参数:",
    ]
    for f in cat["global_flags"]:
        lines.append(f"  {f['flag']:<24} {f['desc']}")
    lines += ["", "退出码:"]
    for code, desc in cat["contract"]["exit_codes"].items():
        lines.append(f"  {code}  {desc}")
    lines += ["", "域:"]
    for d in cat["domains"]:
        mark = "" if d.get("status") == "ok" else "  [不可用]"
        lines.append(f"  {d['name']:<10} {d.get('summary', '')}{mark}")
        for s in d.get("subcommands", []):
            writes = "  [写操作]" if s.get("writes") else ""
            lines.append(f"      {s.get('usage', s.get('name', ''))}{writes}")
            if s.get("summary"):
                lines.append(f"          {s['summary']}")
    return "\n".join(lines)


def main(args: list[str]) -> int:
    if args and args[0] in ("-h", "--help", "help"):
        ui.out("用法: itp-ops.sh catalog [-o json|--json]   # 自描述命令注册表（大模型入口）")
        return 0
    cat = build_catalog()
    ui.result(cat)
    ui.out(render(cat))
    return 0
