"""CloudOS 插件实现 —— 将 cloudos 域迁移到 Plugin 模式。

设计目标：
  · 继承 BasePlugin，实现 execute() 方法
  · 委托给 impl.main() 保持向后兼容
  · 提供 pre_flight_check 做环境连通性自检
  · CATALOG 保持不变

迁移策略：
  · 双轨运行：新 Plugin 入口 + 旧 commands/cloudos.py 兼容
  · 通过环境变量 ITPOPS_PLUGIN_MODE 切换模式
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class CloudOSPlugin(BasePlugin):
    """CloudOS 云管台运维插件。"""

    name = "cloudos"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 cloudos 命令。

        委托给 impl.main() 保持向后兼容。
        """
        # 检查是否启用 Plugin 模式
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            # 兼容模式：直接调用 impl.main
            from .impl import main
            return main(args)

        # Plugin 模式：实现具体逻辑
        from ..core import environments as envx, ui
        from ..errors import EXIT_USAGE, OpsError

        env = envx.init_env("hongdao")
        sub = args[0] if args else ""

        if sub in ("", "help", "-h", "--help"):
            from .impl import _help_text
            ui.out(_help_text())
            return 0

        # 其他命令委托给 impl
        from .impl import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查云管台连通性。

        当前仅检查环境配置，未来可扩展：
        - 检查 token 是否过期
        - 检查网络连通性
        - 检查权限是否足够
        """
        from ..core import environments as envx

        try:
            env = envx.init_env(ctx.env or "hongdao")
            # 基础校验：检查必要配置
            if not env.remote_host:
                return False
            if not env.cloudos_env:
                return False
            return True
        except Exception:
            return False

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from .impl import CATALOG
        return CATALOG


def create_cloudos_plugin() -> CloudOSPlugin:
    """创建 CloudOS Plugin 实例。"""
    return CloudOSPlugin()
