#!/usr/bin/env python3
"""Plugin 迁移辅助脚本。

用途：
  · 扫描 commands/*.py 文件，生成 Plugin 实现模板
  · 验证迁移后的功能正确性

用法：
  python3 migrate_to_plugin.py --scan         # 扫描所有域
  python3 migrate_to_plugin.py --generate k8s # 生成 k8s 域 Plugin 模板
  python3 migrate_to_plugin.py --verify       # 验证所有 Plugin
"""
from __future__ import annotations

import argparse
import importlib
import inspect
import os
import re
import sys
from pathlib import Path
from typing import Any


def scan_commands() -> list[dict]:
    """扫描所有 commands/*.py 文件。"""
    scripts_dir = Path(__file__).resolve().parents[1]
    commands_dir = scripts_dir / "itpops" / "commands"
    
    results = []
    for py_file in sorted(commands_dir.glob("*.py")):
        if py_file.name.startswith("_"):
            continue
        
        domain = py_file.stem
        try:
            spec = importlib.util.spec_from_file_location(f"itpops.commands.{domain}", py_file)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            
            has_catalog = hasattr(mod, "CATALOG")
            has_main = hasattr(mod, "main")
            catalog_size = len(mod.CATALOG.get("subcommands", [])) if has_catalog else 0
            
            results.append({
                "domain": domain,
                "file": str(py_file.relative_to(scripts_dir)),
                "has_catalog": has_catalog,
                "has_main": has_main,
                "subcommands": catalog_size,
                "lines": sum(1 for _ in open(py_file)),
            })
        except Exception as exc:
            results.append({
                "domain": domain,
                "file": str(py_file.relative_to(scripts_dir)),
                "error": str(exc),
            })
    
    return results


def generate_plugin_template(domain: str) -> str:
    """生成指定域的 Plugin 实现模板。"""
    templates = {
        "simple": """\"\"\"{domain} 插件实现。

迁移自 commands/{domain}.py。
\"\"\"
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class {Domain}Plugin(BasePlugin):
    \"\"\"{Domain} 域 Plugin。\"\"\"

    name = "{domain}"

    def execute(self, args: list[str], ctx: PluginContext) -> int:
        \"\"\"执行 {domain} 命令。\"\"\"
        # TODO: 实现具体逻辑
        return 0


def create_{domain}_plugin() -> {Domain}Plugin:
    \"\"\"创建 {domain} Plugin 实例。\"\"\"
    return {Domain}Plugin()
""",
        "complex": """\"\"\"{domain} 插件实现（复杂版本）。

包含 CATALOG 定义、前置校验、后置校验。
\"\"\"
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


CATALOG = {{
    "name": "{domain}",
    "summary": "{domain} 域描述",
    "default_env": "test",
    "subcommands": [],
}}


class {Domain}Plugin(BasePlugin):
    \"\"\"{Domain} 域 Plugin。\"\"\"

    name = "{domain}"

    def get_catalog(self) -> dict:
        \"\"\"返回 CATALOG。\"\"\"
        return CATALOG

    def pre_flight_check(self, ctx: PluginContext) -> bool:
        \"\"\"前置校验。\"\"\"
        # TODO: 添加必要的校验逻辑
        return True

    def execute(self, args: list[str], ctx: PluginContext) -> int:
        \"\"\"执行 {domain} 命令。\"\"\"
        # TODO: 实现具体逻辑
        return 0


def create_{domain}_plugin() -> {Domain}Plugin:
    \"\"\"创建 {domain} Plugin 实例。\"\"\"
    return {Domain}Plugin()
""",
    }
    
    # 根据域的特点选择模板
    simple_domains = {"version", "catalog", "completion", "help"}
    if domain in simple_domains:
        return templates["simple"].format(domain=domain, Domain=domain.capitalize())
    else:
        return templates["complex"].format(domain=domain, Domain=domain.capitalize())


def verify_plugins() -> dict[str, bool]:
    """验证所有 Plugin 是否可以正常导入。"""
    results = {}
    scripts_dir = Path(__file__).resolve().parents[1]
    sys.path.insert(0, str(scripts_dir))
    
    for plugin_file in Path(f"{scripts_dir}/itpops").rglob("plugin.py"):
        domain = plugin_file.parent.name
        try:
            spec = importlib.util.spec_from_file_location(f"itpops.{domain}.plugin", plugin_file)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            results[domain] = True
        except Exception as exc:
            results[domain] = False
            print(f"[ERROR] {domain}: {exc}", file=sys.stderr)
    
    return results


def main():
    parser = argparse.ArgumentParser(description="Plugin 迁移辅助工具")
    parser.add_argument("--scan", action="store_true", help="扫描所有命令域")
    parser.add_argument("--generate", metavar="DOMAIN", help="生成指定域的 Plugin 模板")
    parser.add_argument("--verify", action="store_true", help="验证所有 Plugin")
    args = parser.parse_args()
    
    if args.scan:
        print("=== 命令域扫描 ===")
        for info in scan_commands():
            status = "✓" if info.get("has_catalog") and info.get("has_main") else "✗"
            print(f"{status} {info['domain']:15} {info.get('lines', 0):4} lines  "
                  f"CATALOG={'yes' if info.get('has_catalog') else 'no':3}  "
                  f"sub={info.get('subcommands', 0)}")
            if "error" in info:
                print(f"    ERROR: {info['error']}")
    
    elif args.generate:
        template = generate_plugin_template(args.generate)
        print(template)
    
    elif args.verify:
        print("=== Plugin 验证 ===")
        results = verify_plugins()
        for domain, ok in sorted(results.items()):
            status = "✓" if ok else "✗"
            print(f"{status} {domain}")
    
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
