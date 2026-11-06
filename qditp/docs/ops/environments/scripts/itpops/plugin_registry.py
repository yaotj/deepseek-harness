"""插件注册表 —— 统一管理所有 Plugin 实例。

职责：
  · 注册所有 Plugin 实例到 CommandRegistry
  · 提供 get_plugin(domain) 查询接口
  · 提供 pre_flight_check(domain, ctx) 统一前置校验
  · 支持自动发现所有 Plugin

使用示例：
  from itpops.plugin_registry import get_plugin, check_pre_flight, list_plugins
  
  plugin = get_plugin("cloudos")
  if not check_pre_flight("cloudos", ctx):
      raise OpsError("前置校验失败", EXIT_PRECONDITION)
"""
from __future__ import annotations

from typing import TYPE_CHECKING, Dict, List

if TYPE_CHECKING:
    from .plugin import Plugin, PluginContext

# 插件注册表
_plugins: Dict[str, "Plugin"] = {}


def register(plugin: "Plugin") -> None:
    """注册插件到全局注册表。"""
    _plugins[plugin.name] = plugin


def get_plugin(domain: str) -> "Plugin | None":
    """获取指定域的插件实例。"""
    return _plugins.get(domain)


def list_plugins() -> List[str]:
    """列出所有已注册的插件名。"""
    return sorted(_plugins.keys())


def check_pre_flight(domain: str, ctx: "PluginContext") -> bool:
    """统一前置校验入口。"""
    plugin = _plugins.get(domain)
    if not plugin:
        return True
    return plugin.pre_flight_check(ctx)


def post_flight(domain: str, ctx: "PluginContext", result: dict) -> dict:
    """统一后置校验入口。"""
    plugin = _plugins.get(domain)
    if not plugin:
        return result
    return plugin.post_flight_check(ctx, result)


def auto_register() -> None:
    """自动发现并注册所有 Plugin。"""
    import pkgutil
    import importlib
    import sys
    
    # 扫描 itpops 子包下的 plugin.py
    base_module = sys.modules.get('itpops')
    if not base_module:
        return
    
    base_path = base_module.__path__
    
    for importer, modname, ispkg in pkgutil.walk_packages(
        path=base_path,
        prefix='itpops.'
    ):
        # 查找 plugin.py
        if modname.endswith('.plugin'):
            try:
                mod = importlib.import_module(modname)
                # 尝试创建插件实例
                if hasattr(mod, 'create_'):
                    # 工厂函数模式
                    factory_name = [m for m in dir(mod) if m.startswith('create_')]
                    if factory_name:
                        factory = getattr(mod, factory_name[0])
                        if callable(factory):
                            plugin = factory()
                            register(plugin)
                elif hasattr(mod, 'Plugin'):
                    # 直接类模式（需要 name 属性）
                    plugin_class = getattr(mod, 'Plugin')
                    if hasattr(plugin_class, 'name'):
                        plugin = plugin_class()
                        register(plugin)
            except Exception as exc:
                # 静默忽略导入失败的模块
                pass


# 启动时自动注册
auto_register()
