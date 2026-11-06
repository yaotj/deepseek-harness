"""Version 插件实现 —— 版本号计算与载体 bump。

继承 BasePlugin，委托 commands/version.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class VersionPlugin(BasePlugin):
    """版本号计算与载体 bump 插件。"""

    name = "version"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 version 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.version import main
            return main(args)
        
        from ..commands.version import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：环境无关，始终通过。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.version import CATALOG
        return CATALOG


def create_version_plugin() -> VersionPlugin:
    """创建 Version Plugin 实例。"""
    return VersionPlugin()
