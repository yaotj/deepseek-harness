"""itpops catalog —— 给大模型的自描述命令注册表。

双受众契约的一半：人看 `itp-ops.sh <域> help` / 总用法；大模型看
`itp-ops.sh catalog --json` 拿到全量机器可读命令面（域、子命令、参数、
是否写操作、默认环境、示例、退出码语义）。

新增域时：在域模块里写 CATALOG（契约见 domains/__init__.py），并把
「模块名」登记进下方 _DOMAIN_MODULES —— catalog 会自动汇总。
"""
from __future__ import annotations

import importlib

from . import ITP_OPS_VERSION
from .core import ui
from .errors import CODE_BY_EXIT

# 域名 → itpops.domains 下的模块名（与 itp-ops.sh v1 命令面一致）
_DOMAIN_MODULES: dict[str, str] = {
    "version": "version_cmd",
    "cloudos": "cloudos",
    "k8s": "k8s",
    "log": "log",
    "diag": "diag",
    "analysis": "analysis",
}

# 独立入口（不经 itp-ops 域分发，原名直接调用；CATALOG 含 standalone: True）
_STANDALONE_MODULES: tuple[str, ...] = (
    "buildx",
    "route_sentinel",
    "web.frontend_release",
    "web.mount_export",
)

EXIT_CODE_DOC = {
    "0": "成功",
    "1": "未分类错误（尽量别用）",
    "2": "用法错误（参数不对 / 未知域 / 未知子命令）",
    "3": "前置不满足（缺依赖、仓库根断言失败、环境未知）",
    "4": "远端/传输失败（ssh 不可达、HTTP 连不上）",
    "5": "命令执行失败（远端命令非 0、业务拒绝、解析失败）",
    "6": "写操作缺 --yes 被拒",
    "130": "中断（Ctrl+C）",
}

GLOBAL_FLAGS_DOC = [
    {"flag": "-e, --env hongdao|test", "desc": "环境切换（全局，所有域生效）。未指定时用各域自己的默认（cloudos=hongdao、k8s/log=test、diag 按子命令）"},
    {"flag": "-y, --yes", "desc": "确认写操作；缺它写命令一律拒绝"},
    {"flag": "-n, --dry-run", "desc": "强制预演（等价不带 --yes，优先于 --yes）；命令无预演分支时报用法错"},
    {"flag": "-o, --output text|json", "desc": "输出格式；--json 为 -o json 的简写"},
    {"flag": "-q, --quiet", "desc": "抑制 INFO/STEP 日志（WARN/ERROR 保留）"},
    {"flag": "-v, --verbose", "desc": "DEBUG 输出（远端命令行等），可叠加 -vv"},
    {"flag": "--no-color", "desc": "关闭颜色（亦可用 NO_COLOR 环境变量）"},
    {"flag": "--trace", "desc": "出错时打印完整堆栈"},
    {"flag": "-h, --help", "desc": "帮助（任意层级有效：域级富文本，命令级由 CATALOG 生成）"},
    {"flag": "-V, --version", "desc": "版本信息（含 python / git commit）"},
    {"flag": "--", "desc": "终止选项解析，其后的参数按位置参数处理"},
]


def _load_domains() -> list[dict]:
    """汇总已移植域的 CATALOG；尚未移植的域以占位项标注（迁移期可见）。"""
    domains: list[dict] = []
    for name, module_name in _DOMAIN_MODULES.items():
        try:
            mod = importlib.import_module(f".domains.{module_name}", package="itpops")
        except ImportError:
            domains.append({
                "name": name,
                "status": "pending",
                "summary": "尚未移植到 Python（迁移期可暂用 itp-ops.legacy.sh）",
                "subcommands": [],
            })
            continue
        entry = dict(getattr(mod, "CATALOG", {"name": name, "subcommands": []}))
        entry.setdefault("name", name)
        entry["status"] = "ok"
        domains.append(entry)
    return domains


def _load_standalone() -> list[dict]:
    """汇总独立入口（buildx / route_sentinel / web 等）的 CATALOG。"""
    tools: list[dict] = []
    for module_name in _STANDALONE_MODULES:
        try:
            mod = importlib.import_module(f".{module_name}", package="itpops")
        except ImportError:
            tools.append({
                "name": module_name,
                "status": "pending",
                "summary": "尚未移植到 Python",
            })
            continue
        entry = dict(getattr(mod, "CATALOG", {"name": module_name}))
        entry.setdefault("name", module_name)
        entry["status"] = "ok"
        tools.append(entry)
    return tools


def build_catalog() -> dict:
    return {
        "tool": "itp-ops",
        "version": ITP_OPS_VERSION,
        "contract": {
            "stdout": "人类结果文本；-o json 时仅一条 JSON 信封",
            "stderr": "人机日志（INFO/WARN/ERROR/STEP/DEBUG/环境横幅），TTY 才着色",
            "output_formats": ["text", "json"],
            "json_envelope": {"ok": "bool", "command": "list[str]", "data": "object|null", "error": "{code,message,exit}|null", "duration_ms": "int"},
            "exit_codes": EXIT_CODE_DOC,
            "code_names": {str(k): v for k, v in CODE_BY_EXIT.items()},
            "env_vars": {"NO_COLOR": "关闭颜色", "CLICOLOR_FORCE": "强制颜色", "ITP_OPS_ENV": "默认环境（等价 --env，显式 --env 优先）"},
            "help": {"root": "itp-ops.sh -h", "domain": "itp-ops.sh <域> help", "command": "itp-ops.sh <域> <命令> -h"},
            "completion": "itp-ops.sh completion bash|zsh",
        },
        "global_flags": GLOBAL_FLAGS_DOC,
        "domains": _load_domains(),
        "standalone": _load_standalone(),
    }


def _render_human(cat: dict) -> str:
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
        mark = "" if d.get("status") == "ok" else "  [待移植]"
        lines.append(f"  {d['name']:<10} {d.get('summary', '')}{mark}")
        for s in d.get("subcommands", []):
            writes = "  [写操作]" if s.get("writes") else ""
            lines.append(f"      {s.get('usage', s.get('name', ''))}{writes}")
            if s.get("summary"):
                lines.append(f"          {s['summary']}")
    if cat.get("standalone"):
        lines += ["", "独立入口（原名直接调用）:"]
        for t in cat["standalone"]:
            mark = "" if t.get("status") == "ok" else "  [待移植]"
            lines.append(f"  {t['name']:<28} {t.get('summary', '')}{mark}")
            if t.get("usage"):
                lines.append(f"      {t['usage']}")
    return "\n".join(lines)


def main(args: list[str]) -> int:
    if args and args[0] in ("-h", "--help", "help"):
        ui.out("用法: itp-ops.sh catalog [-o json|--json]   # 自描述命令注册表（大模型入口）")
        return 0
    cat = build_catalog()
    ui.result(cat)
    ui.out(_render_human(cat))
    return 0
