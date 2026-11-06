"""Catalog 插件实现 —— 命令注册表插件。

继承 BasePlugin，委托 commands/catalog.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class CatalogPlugin(BasePlugin):
    """命令注册表插件。"""

    name = "catalog"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 catalog 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.catalog import main
            return main(args)
        
        from ..commands.catalog import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：环境无关，始终通过。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.catalog import CATALOG
        return CATALOG


def create_catalog_plugin() -> CatalogPlugin:
    """创建 Catalog Plugin 实例。"""
    return CatalogPlugin()
