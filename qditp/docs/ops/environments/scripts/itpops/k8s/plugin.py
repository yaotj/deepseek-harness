"""K8s 插件实现 —— 镜像构建推送插件。

继承 BasePlugin，委托 commands/k8s.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class K8sPlugin(BasePlugin):
    """K8s 镜像构建推送插件。"""

    name = "k8s"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 k8s 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.k8s import main
            return main(args)
        
        # Plugin 模式：直接调用 commands/k8s.py 的 main
        from ..commands.k8s import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查 SSH 和 SCP 命令可用。"""
        from ..core.runtime import need_cmd
        try:
            need_cmd("ssh", "scp")
            return True
        except Exception:
            return False

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.k8s import CATALOG
        return CATALOG


def create_k8s_plugin() -> K8sPlugin:
    """创建 K8s Plugin 实例。"""
    return K8sPlugin()
