"""Lint 插件实现 —— 仓库门禁插件。

继承 BasePlugin，委托 commands/lint.py 的 main()。
"""
from __future__ import annotations

import os
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin_base import PluginContext

from ..plugin_base import BasePlugin


class LintPlugin(BasePlugin):
    """仓库门禁插件。"""

    name = "lint"

    def execute(self, args: list[str], ctx: "PluginContext") -> int:
        """执行 lint 命令。"""
        if os.environ.get("ITPOPS_PLUGIN_MODE") != "1":
            from ..commands.lint import main
            return main(args)
        
        from ..commands.lint import main as _main
        return _main(args)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查 Python3 和 PyYAML 可用。"""
        from ..core.runtime import need_cmd
        try:
            need_cmd("python3")
            # 尝试导入 PyYAML
            import yaml  # noqa: F401
            return True
        except Exception:
            return False

    def get_catalog(self) -> dict:
        """获取域的 CATALOG。"""
        from ..commands.lint import CATALOG
        return CATALOG


def create_lint_plugin() -> LintPlugin:
    """创建 Lint Plugin 实例。"""
    return LintPlugin()
