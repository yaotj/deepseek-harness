"""Plugin 自动注册模块。

在 itpops 包初始化时自动扫描并注册所有 Plugin。
"""
from __future__ import annotations

import pkgutil
import sys


def auto_register_plugins() -> None:
    """自动发现并注册所有 Plugin。
    
    扫描 itpops 包下所有子包的 plugin.py 文件，
    找到 create_<domain>_plugin() 工厂函数并调用。
    """
    import itpops
    
    base_path = itpops.__path__[0]
    
    for importer, modname, ispkg in pkgutil.walk_packages(
        path=[base_path],
        prefix='itpops.'
    ):
        # 查找 plugin.py
        if modname.endswith('.plugin'):
            try:
                mod = __import__(modname, fromlist=[''])
                
                # 尝试工厂函数模式
                factory_name = f'create_{modname.split(".")[-2]}_plugin'
                if hasattr(mod, factory_name):
                    factory = getattr(mod, factory_name)
                    if callable(factory):
                        plugin = factory()
                        from .plugin_registry import register
                        register(plugin)
                        continue
                
                # 尝试直接类模式
                if hasattr(mod, 'Plugin'):
                    plugin_class = getattr(mod, 'Plugin')
                    if hasattr(plugin_class, 'name'):
                        plugin = plugin_class()
                        from .plugin_registry import register
                        register(plugin)
                        
            except Exception:
                pass  # 静默忽略导入失败的模块


# 模块加载时自动注册
auto_register_plugins()
