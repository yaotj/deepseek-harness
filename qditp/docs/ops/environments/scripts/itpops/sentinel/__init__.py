"""sentinel 特性包 —— route-sentinel 首次部署实现。

命令入口在 `itpops/commands/sentinel.py`；本包只放实现与 CATALOG 元数据。
"""
from .core import CATALOG, main

__all__ = ["CATALOG", "main"]
