"""帮助渲染 —— 对齐 cobra/kubectl 的分层帮助结构。

分工（避免两套 help 打架）：
  · 根帮助 / 命令级帮助（`<域> <命令> -h`）→ 本模块从 CATALOG 生成，结构统一：
    USAGE / ARGS / EXAMPLE / OUTPUT / NOTES
  · 域级帮助（`<域> help`、`<域> -h`）→ 仍由各域自带的富文本提供（含平台坑位说明，
    那是有价值的运维知识，生成式帮助替代不了）。
"""
from __future__ import annotations

import sys

from .registry import load_domains


def _arg_line(a) -> str:
    """CATALOG 的 args 条目兼容 dict / 字符串两种写法。"""
    if isinstance(a, dict):
        name = a.get("name", "")
        desc = a.get("desc") or a.get("description") or ""
        req = a.get("required")
        suffix = "" if req is None else ("（必填）" if req else "（可选）")
        return f"  {name:<16} {desc}{suffix}".rstrip()
    return f"  {a}"


def find_domain(name: str) -> dict | None:
    for d in load_domains():
        if d.get("name") == name:
            return d
    return None


def find_subcommand(domain: str, sub: str) -> dict | None:
    d = find_domain(domain)
    if not d:
        return None
    for s in d.get("subcommands", []):
        if s.get("name") == sub:
            return s
    return None


def render_command_help(domain: str, sub: dict) -> str:
    """单条命令的帮助（`itp-ops <域> <命令> -h`）。"""
    name = sub.get("name", "")
    summary = sub.get("summary", "")
    lines: list[str] = []
    head = f"itp-ops {domain} {name}"
    lines.append(f"{head} — {summary}" if summary else head)
    lines.append("")
    lines.append("用法:")
    usage = sub.get("usage") or f"{domain} {name}"
    lines.append(f"  itp-ops.sh {usage}")
    lines.append("")

    args = sub.get("args") or []
    if args:
        lines.append("参数:")
        lines.extend(_arg_line(a) for a in args)
        lines.append("")

    flags: list[str] = []
    if sub.get("writes"):
        flags.append("  -y, --yes          确认执行写操作（缺 --yes 拒绝执行）")
        flags.append("  -n, --dry-run      强制预演（优先于 --yes；无预演分支的命令会报错）")
    glob = "  -e/--env  -o/--output  -q/--quiet  -v/--verbose  --no-color"
    if flags:
        lines.append("常用全局参数:")
        lines.extend(flags)
        lines.append(glob)
    else:
        lines.append("常用全局参数:")
        lines.append(glob)
    lines.append("")

    example = sub.get("example")
    if example:
        lines.append("示例:")
        lines.append(f"  {example}")
        lines.append("")

    out_bits: list[str] = []
    if sub.get("stdout_human"):
        out_bits.append(f"text: {sub['stdout_human']}")
    if sub.get("json_data") is not None:
        out_bits.append(f"json(-o json): data={sub['json_data']!r}")
    if sub.get("json") is False:
        out_bits.append("该命令为流式输出，不支持 -o json")
    if out_bits:
        lines.append("输出:")
        lines.extend(f"  {b}" for b in out_bits)
        lines.append("")

    d = find_domain(domain)
    env_note = (d or {}).get("env_note")
    if env_note:
        lines.append(f"环境: {env_note}")
    return "\n".join(lines).rstrip()


def maybe_handle_help(domain: str, args: list[str]) -> bool:
    """命令级 -h 拦截：`<域> <命令> -h`（命令可为多词，如 `image build`）。

    域级 help（无子命令 / `help` 子命令）返回 False，交给域自带的富文本。
    命中返回 True 并已打印帮助。
    """
    if not any(a in ("-h", "--help") for a in args):
        return False
    positionals = [a for a in args if not a.startswith("-")]
    # 先按两词匹配（image build / svc create），再退一词
    for n in (2, 1):
        if len(positionals) >= n:
            name = " ".join(positionals[:n])
            if name == "help":
                return False
            entry = find_subcommand(domain, name)
            if entry:
                print(render_command_help(domain, entry), file=sys.stdout)
                return True
    return False
