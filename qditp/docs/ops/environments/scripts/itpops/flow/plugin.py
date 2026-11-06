"""Flow 插件实现 —— 工作流一键入口插件。

继承 BasePlugin，委托 commands/flow.py 的 main()。
支持所有工作流：health/backend/frontend/jvm/rollback/integration/rollback-plan/batch-jvm/batch-bump/cleanup/health-report。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class FlowPlugin(BasePlugin):
    """工作流一键入口插件。"""

    name = "flow"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 flow 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.flow import main
            return main(args)
        
        from ..commands.flow import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：环境无关，始终通过。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.flow import CATALOG
        return CATALOG


def create_flow_plugin() -> FlowPlugin:
    """创建 Flow Plugin 实例。"""
    return FlowPlugin()
