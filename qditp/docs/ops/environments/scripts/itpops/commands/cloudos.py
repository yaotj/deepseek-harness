"""itpops · cloudos 域「命令层」—— 只做注册与转发，不含实现。

分层纪律（见 CONTRIBUTING.md）：`commands/` 一个文件 = 一个命令域，暴露
`main(args) -> int` 与 `CATALOG`；凡是超出「参数分发 + 输出编排」的实现 MUST
下沉到特性包。cloudos 是全仓最大的域（30 子命令 / 1776 行实现），2026-11-04
整块下沉到 `itpops/cloudos/impl.py`（含历史坑位说明与 CATALOG 全文）。

`itpops/cli/registry.py` 通过本模块取 CATALOG，`cli/main.py` 通过本模块取 main；
这里用重导出保持域契约与命令面 100% 兼容（help / stdout / catalog 逐字未变）。
"""
from __future__ import annotations

from ..cloudos.impl import CATALOG, main  # noqa: F401 —— 域契约要求同名导出

__all__ = ["CATALOG", "main"]
