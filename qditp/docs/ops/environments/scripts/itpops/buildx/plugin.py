"""Buildx 插件实现 —— 镜像构建插件。

继承 BasePlugin，委托给 core.main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class BuildXPlugin(BasePlugin):
    """镜像构建插件。"""

    name = "buildx"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 buildx 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from .core import main
            return main(args)
        
        from .core import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查构建环境。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from .core import CATALOG
        return CATALOG


def create_buildx_plugin() -> BuildXPlugin:
    """创建 BuildX Plugin 实例。"""
    return BuildXPlugin()
