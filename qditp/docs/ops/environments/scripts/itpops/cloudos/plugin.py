"""CloudOS 插件实现 —— 将 cloudos 域迁移到 Plugin 模式。

设计目标：
  · 继承 Plugin ABC，实现 register() / pre_flight_check()
  · CATALOG 保持不变，确保向后兼容
  · 作为插件架构的参考实现

注意：
  · 当前 cloudos 域仍通过 commands/cloudos.py 直接调用 impl.main()
  · Plugin 模式为未来迁移做准备，不影响现有功能
"""
from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..plugin import CommandRegistry, PluginContext


class CloudOSPlugin:
    """CloudOS 云管台运维插件。

    职责：
      · 注册 cloudos 域的所有命令到 CommandRegistry
      · 提供 pre_flight_check 做环境连通性自检
      · 提供 post_flight_check 做结果验证
    """

    name = "cloudos"

    def __init__(self) -> None:
        # 延迟导入 CATALOG（避免循环依赖）
        self._catalog = None

    def register(self, registry: "CommandRegistry") -> None:
        """注册 cloudos 域到命令注册表。"""
        from .impl import CATALOG
        registry.add_domain(CATALOG)

    def pre_flight_check(self, ctx: "PluginContext") -> bool:
        """前置校验：检查云管台连通性。

        参数:
          ctx: 插件执行上下文

        返回:
          True: 校验通过
          False: 校验失败（拒绝执行写操作）
        """
        # TODO: 未来可在此处添加更严格的前置检查
        # 例如：检查 token 是否过期、检查网络连通性等
        return True

    def post_flight_check(self, ctx: "PluginContext", result: dict) -> dict:
        """后置校验：验证命令执行结果。

        参数:
          ctx: 插件执行上下文
          result: 命令执行结果

        返回:
          清洗后的结果字典
        """
        # TODO: 未来可在此处添加结果验证逻辑
        # 例如：检查应用状态、检查 Pod 就绪等
        return result

    def get_catalog(self) -> dict:
        """获取域的 CATALOG（供老代码兼容使用）。"""
        from .impl import CATALOG
        return CATALOG


# 工厂函数：便于扩展其他插件
def create_cloudos_plugin() -> CloudOSPlugin:
    """创建 CloudOS 插件实例。"""
    return CloudOSPlugin()
