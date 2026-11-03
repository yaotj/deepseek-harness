"""itpops.cli —— 表现层（参数解析、帮助、补全、输出契约）。

对标 pip `_internal/cli/` 的分层：
  main.py       入口：全局参数解析 + 域分发 + JSON 信封 + 错误渲染
  registry.py   域清单与 CATALOG 汇总（纯数据）
  help.py       分层帮助渲染（命令级由 CATALOG 生成）
  completion.py bash/zsh 补全生成（从 CATALOG 派生）
"""
from .main import main

__all__ = ["main"]
