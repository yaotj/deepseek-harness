"""Web 插件 —— 前端发版插件框架。

职责：
  · 封装 web 前端发版逻辑（zip 构建 + 软件中心上传 + 升级）
  · 提供 release / mount-export 等命令
  · 未来可扩展为独立插件

注意：
  · 当前 web 域仍通过 commands/web.py 直接调用实现
  · 此插件为未来迁移做准备
"""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin import CommandRegistry, PluginContext


class WebPlugin:
    """前端发版插件。"""

    name = "web"

    def register(self, registry: "CommandRegistry") -> None:
        """注册 web 域到命令注册表。"""
        from .impl import CATALOG
        registry.add_domain(CATALOG)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查前端构建环境。"""
        # TODO: 检查 Node.js 版本、npm 缓存等
        return True

    def post_flight_check(self, ctx: "PluginContext", result: dict) -> dict:
        """后置校验：验证前端发版结果。"""
        # TODO: 检查软件中心上传状态
        return result


def create_web_plugin() -> WebPlugin:
    """创建 Web 插件实例。"""
    return WebPlugin()
