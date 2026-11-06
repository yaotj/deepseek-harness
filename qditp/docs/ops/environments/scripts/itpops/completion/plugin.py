"""Completion 插件实现 —— Shell 补全生成插件。

继承 BasePlugin，委托 cli.completion 模块的功能。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class CompletionPlugin(BasePlugin):
    """Shell 补全生成插件。"""

    name = "completion"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 completion 命令。"""
        from ..cli.completion import main as completion_main
        return completion_main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：环境无关，始终通过。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG（无子命令）。"""
        return {
            "name": "completion",
            "summary": "shell 补全脚本（bash / zsh）",
            "default_env": None,
            "subcommands": [],
        }


def create_completion_plugin() -> CompletionPlugin:
    """创建 Completion Plugin 实例。"""
    return CompletionPlugin()
