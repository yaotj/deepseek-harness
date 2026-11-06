"""Web 插件实现 —— 前端发版插件。

继承 BasePlugin，委托给 frontend_release/main 和 mount_export/main。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class WebPlugin(BasePlugin):
    """前端发版插件。"""

    name = "web"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 web 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.web import main
            return main(args)
        
        sub = args[0] if args else "help"
        rest = args[1:]
        
        from ..core import ui
        if sub in ("help", "-h", "--help"):
            from ..commands.web import _HELP
            ui.out(_HELP)
            return 0
        elif sub == "release":
            from ..web.frontend_release import main as release_main
            return release_main(rest)
        elif sub == "mount-export":
            from ..web.mount_export import main as mount_main
            return mount_main(rest)
        else:
            from ..errors import EXIT_USAGE, OpsError
            raise OpsError(f"未知子命令：{sub}", EXIT_USAGE)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查前端构建环境。"""
        return True

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.web import CATALOG
        return CATALOG


def create_web_plugin() -> WebPlugin:
    """创建 Web Plugin 实例。"""
    return WebPlugin()
