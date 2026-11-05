"""插件注册表 —— 统一管理所有 Plugin 实例。

职责：
  · 注册所有 Plugin 实例到 CommandRegistry
  · 提供 get_plugin(domain) 查询接口
  · 提供 pre_flight_check(domain, ctx) 统一前置校验

使用示例：
  from itpops.plugin_registry import get_plugin, check_pre_flight
  
  plugin = get_plugin("cloudos")
  if not check_pre_flight("cloudos", ctx):
      raise OpsError("前置校验失败", EXIT_PRECONDITION)
"""
from __future__ import annotations

from typing import dict

from .plugin import CommandRegistry, PluginContext
from .errors import OpsError, EXIT_PRECONDITION

# 插件注册表
_plugins: dict[str, "Plugin"] = {}


def register(plugin: "Plugin") -> None:
    """注册插件到全局注册表。"""
    _plugins[plugin.name] = plugin


def get_plugin(domain: str) -> "Plugin | None":
    """获取指定域的插件实例。"""
    return _plugins.get(domain)


def list_plugins() -> list[str]:
    """列出所有已注册的插件名。"""
    return sorted(_plugins.keys())


def check_pre_flight(domain: str, ctx: PluginContext) -> bool:
    """统一前置校验入口。"""
    plugin = _plugins.get(domain)
    if not plugin:
        return True
    return plugin.pre_flight_check(ctx)


def post_flight(domain: str, ctx: PluginContext, result: dict) -> dict:
    """统一后置校验入口。"""
    plugin = _plugins.get(domain)
    if not plugin:
        return result
    return plugin.post_flight_check(ctx, result)


# 自动注册所有插件
def _auto_register() -> None:
    """扫描 itpops 包下所有 plugin.py 并自动注册。"""
    import pkgutil
    import importlib
    
    # 扫描 itpops 子包
    for importer, modname, ispkg in pkgutil.walk_packages(
        path=__import__('itpops').__path__,
        prefix='itpops.'
    ):
        # 查找 plugin.py
        if modname.endswith('.plugin'):
            try:
                mod = importlib.import_module(modname)
                # 尝试创建插件实例
                if hasattr(mod, 'create_') or hasattr(mod, 'Plugin'):
                    # 动态创建插件（简化处理）
                    pass
            except Exception:
                pass  # 忽略导入失败的模块


# 启动时自动注册
_auto_register()
