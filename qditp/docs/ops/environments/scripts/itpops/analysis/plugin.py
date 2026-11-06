"""Analysis 插件实现 —— 代码静态分析插件。

继承 BasePlugin，委托 commands/analysis.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class AnalysisPlugin(BasePlugin):
    """代码静态分析插件。"""

    name = "analysis"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 analysis 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.analysis import main
            return main(args)
        
        from ..commands.analysis import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查 Python3 可用。"""
        from ..core.runtime import need_cmd
        try:
            need_cmd("python3")
            return True
        except Exception:
            return False

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.analysis import CATALOG
        return CATALOG


def create_analysis_plugin() -> AnalysisPlugin:
    """创建 Analysis Plugin 实例。"""
    return AnalysisPlugin()
