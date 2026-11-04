"""python -m itpops.buildx —— 调试别名，转发到唯一入口 `itpops.cli.main`。

正式入口是 `itp-ops.sh buildx`（等价 `python -m itpops buildx`）。
2026-11-04 起这里不再直接调 `buildx.core.main`：那样会绕过全局参数解析、
JSON 信封与退出码契约，形成事实上的第二个入口。改为注入域名后交给统一入口，
保证所有调用路径的 `--env` / `-o json` / `--yes` / 错误信封语义完全一致。
"""
from __future__ import annotations

import sys

from ..cli.main import main

if __name__ == "__main__":
    sys.exit(main(["buildx", *sys.argv[1:]]))
