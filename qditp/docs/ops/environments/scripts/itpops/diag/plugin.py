"""Diag 插件实现 —— 网络诊断插件。

继承 BasePlugin，委托 commands/diag.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class DiagPlugin(BasePlugin):
    """网络诊断插件。"""

    name = "diag"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 diag 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.diag import main
            return main(args)
        
        # Plugin 模式：直接调用 commands/diag.py 的 main
        from ..commands.diag import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查 SSH 和 Python3 命令可用。"""
        from ..core.runtime import need_cmd
        try:
            need_cmd("ssh", "python3")
            return True
        except Exception:
            return False

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.diag import CATALOG
        return CATALOG


def create_diag_plugin() -> DiagPlugin:
    """创建 Diag Plugin 实例。"""
    return DiagPlugin()
