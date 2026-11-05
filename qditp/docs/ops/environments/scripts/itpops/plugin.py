"""插件架构设计 —— Plugin ABC 接口定义。

设计目标：
  · 每个业务域（cloudos/buildx/web/sentinel...）作为独立插件，可单独测试、单独 release
  · 插件通过 register() 方法向 CommandRegistry 注册命令
  · 插件可选实现 pre_flight_check() 做前置校验
  · 当前阶段：定义接口 + cloudos 作为参考实现，其他域后续迁移

使用示例：
  class CloudOSPlugin(Plugin):
      name = "cloudos"
      def register(self, registry):
          registry.add_domain(CATALOG)
      def pre_flight_check(self, ctx):
          return check_cloudos_connectivity(ctx.env)

注意：这是架构设计文档，具体实现见 itpops/cloudos/impl.py（已按此模式组织）。
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any


@dataclass
class PluginContext:
    """插件执行上下文。"""
    env: str
    yes: bool
    dry_run: bool
    json_mode: bool
    verbose: int
    quiet: bool


class Plugin(ABC):
    """插件基类。所有业务域 MUST 继承此类。"""

    @property
    @abstractmethod
    def name(self) -> str:
        """插件名称（对应 itp-ops.sh 的域名）。"""
        ...

    @abstractmethod
    def register(self, registry: "CommandRegistry") -> None:
        """向命令注册表注册本插件的所有命令。"""
        ...

    def pre_flight_check(self, ctx: PluginContext) -> bool:
        """前置校验（可选覆写）。失败则拒绝执行写操作。

        默认实现返回 True（不拦截）。
        """
        return True

    def post_flight_check(self, ctx: PluginContext, result: dict) -> dict:
        """后置校验（可选覆写）。用于结果验证/清洗。

        默认实现原样返回。
        """
        return result


class CommandRegistry:
    """命令注册表。插件通过此接口注册命令。"""

    def __init__(self) -> None:
        self._domains: dict[str, dict] = {}

    def add_domain(self, catalog: dict) -> None:
        """注册一个域（domain）。"""
        name = catalog.get("name")
        if not name:
            raise ValueError("catalog 必须包含 'name' 字段")
        self._domains[name] = catalog

    def get_domain(self, name: str) -> dict | None:
        """获取域的 CATALOG。"""
        return self._domains.get(name)

    def list_domains(self) -> list[str]:
        """列出所有注册的域。"""
        return sorted(self._domains.keys())


# 前向引用
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from .commands.flow import CATALOG as FLOW_CATALOG  # noqa: F401
