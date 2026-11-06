"""Help 插件实现 —— 帮助系统插件。

继承 BasePlugin，委托 cli.help 模块的功能。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class HelpPlugin(BasePlugin):
    """帮助系统插件。"""

    name = "help"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 help 命令。"""
        from ..cli.help import main as help_main
        return help_main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：环境无关，始终通过。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG（无子命令）。"""
        return {
            "name": "help",
            "summary": "帮助（任意层级有效）",
            "default_env": None,
            "subcommands": [],
        }


def create_help_plugin() -> HelpPlugin:
    """创建 Help Plugin 实例。"""
    return HelpPlugin()
