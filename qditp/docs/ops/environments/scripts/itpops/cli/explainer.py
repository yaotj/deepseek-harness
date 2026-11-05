"""--explain 命令解释开关 —— 打印「这条命令做了什么、为什么这么做、改了什么」。

设计目标：
  · 所有命令支持 --explain 开关，打印命令的行为解释
  · 解释内容由 CATALOG 的 explain 字段提供（新增字段）
  · 帮助模式（-h/--help）自动包含解释信息
  · 对新人是极好的学习工具

使用示例：
  itp-ops.sh cloudos apps --explain
  itp-ops.sh flow backend face-pay-server --explain
  itp-ops.sh version next 1.2.42 --explain

实现要点：
  · 在 main.py 的参数解析阶段检测 --explain
  · 调用 explainer.explain(domain, subcommand, args) 获取解释文本
  · 打印解释后退出（不执行实际命令）
"""
from __future__ import annotations

import sys
from typing import Any


def explain_command(domain: str, subcommand: str, args: list[str], catalog: dict) -> str:
    """生成命令解释文本。

    参数:
      domain: 域名称（如 cloudos, flow, version）
      subcommand: 子命令名称（如 apps, backend, next）
      args: 用户提供的参数列表
      catalog: 域的 CATALOG 字典

    返回:
      解释文本
    """
    lines: list[str] = []

    # 查找子命令的 CATALOG 条目
    cmd_info = _find_command(catalog, subcommand)
    if not cmd_info:
        return f"未找到命令 {domain} {subcommand} 的解释信息。"

    # 标题
    lines.append(f"===== 命令解释：{domain} {subcommand} =====")
    lines.append("")

    # 功能说明
    summary = cmd_info.get("summary", "")
    if summary:
        lines.append(f"【功能】{summary}")
        lines.append("")

    # 用法
    usage = cmd_info.get("usage", "")
    if usage:
        lines.append(f"【用法】{usage}")
        lines.append("")

    # 参数说明
    cmd_args = cmd_info.get("args", [])
    if cmd_args:
        lines.append("【参数】")
        for arg in cmd_args:
            name = arg.get("name", "")
            required = arg.get("required", False)
            lines.append(f"  - {name}{'（必填）' if required else '（可选）'}")
        lines.append("")

    # 写操作说明
    writes = cmd_info.get("writes", False)
    if writes:
        lines.append("【写操作】⚠️ 此命令会修改环境状态，需要加 --yes 确认。")
        lines.append("")

    # 示例
    example = cmd_info.get("example", "")
    if example:
        lines.append(f"【示例】{example}")
        lines.append("")

    # 输出说明
    stdout_human = cmd_info.get("stdout_human", "")
    if stdout_human:
        lines.append("【输出】")
        for line in stdout_human.split("\n"):
            lines.append(f"  {line}")
        lines.append("")

    # 环境与注意事项
    lines.append("【环境与注意事项】")
    lines.append(f"  - 默认环境：{catalog.get('default_env', 'test')}")
    lines.append(f"  - 环境说明：{catalog.get('env_note', '无')}")
    lines.append("")

    # 底层实现说明（从实现代码推断）
    lines.append("【实现要点】")
    lines.append(f"  - 域入口：itpops.commands.{domain}.main()")
    lines.append(f"  - 核心实现：itpops/{domain.replace('-', '/')}/impl.py")
    lines.append("")

    return "\n".join(lines)


def _find_command(catalog: dict, subcommand: str) -> dict | None:
    """在 CATALOG 中查找子命令信息。"""
    for cmd in catalog.get("subcommands", []):
        if cmd.get("name") == subcommand:
            return cmd
    return None


def handle_explain(args: list[str]) -> int:
    """处理 --explain 请求。

    参数:
      args: 原始命令行参数

    返回:
      退出码（0 成功）
    """
    from ..cli.registry import load_domains
    from ..core import ui

    # 解析域和子命令
    if len(args) < 2:
        ui.out("用法：itp-ops.sh <域> <命令> --explain")
        ui.out("")
        ui.out("示例：")
        ui.out("  itp-ops.sh cloudos apps --explain")
        ui.out("  itp-ops.sh flow backend face-pay-server --explain")
        ui.out("  itp-ops.sh version next 1.2.42 --explain")
        return 0

    domain = args[0]
    subcommand = args[1]
    cmd_args = args[2:]

    # 加载域 CATALOG
    domains = load_domains()
    catalog = None
    for d in domains:
        if d.get("name") == domain:
            catalog = d
            break

    if not catalog:
        ui.out(f"错误：未知域 {domain}")
        ui.out(f"可用域：{', '.join(d.get('name', '') for d in domains)}")
        return 2

    # 生成解释
    explanation = explain_command(domain, subcommand, cmd_args, catalog)
    ui.out(explanation)

    return 0


def add_explain_to_catalog(catalog: dict) -> dict:
    """为 CATALOG 添加 explain 字段（占位符）。

    实际解释内容由 explainer.explain_command() 动态生成。
    此函数用于标记哪些命令支持 --explain。
    """
    for cmd in catalog.get("subcommands", []):
        if "explain" not in cmd:
            cmd["explain"] = True  # 标记为支持 --explain
    return catalog
