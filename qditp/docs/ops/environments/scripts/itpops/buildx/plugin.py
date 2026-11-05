"""Buildx 插件 —— 镜像构建插件框架。

职责：
  · 封装平台 x86 节点的镜像构建逻辑
  · 提供 build / plan / probe 等命令
  · 未来可扩展为独立插件

注意：
  · 当前 buildx 域仍通过 commands/buildx.py 直接调用实现
  · 此插件为未来迁移做准备
"""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin import CommandRegistry, PluginContext


class BuildXPlugin:
    """镜像构建插件。"""

    name = "buildx"

    def register(self, registry: "CommandRegistry") -> None:
        """注册 buildx 域到命令注册表。"""
        from .impl import CATALOG
        registry.add_domain(CATALOG)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查构建环境可用性。"""
        # TODO: 检查平台 x86 节点可达性
        return True

    def post_flight_check(self, ctx: "PluginContext", result: dict) -> dict:
        """后置校验：验证镜像构建结果。"""
        # TODO: 检查镜像 digest 是否匹配
        return result


def create_buildx_plugin() -> BuildXPlugin:
    """创建 BuildX 插件实例。"""
    return BuildXPlugin()
