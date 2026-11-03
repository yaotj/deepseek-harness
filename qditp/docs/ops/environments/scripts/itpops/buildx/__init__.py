"""buildx 特性包 —— 镜像构建实现（CloudOS hcr file/upload + buildx 端点）。

命令入口在 `itpops/commands/buildx.py`；本包只放实现与 CATALOG 元数据。
`python3 -m itpops.buildx` 仍可直接调用（保留给脚本/调试）。
"""
from .core import CATALOG, main

__all__ = ["CATALOG", "main"]
