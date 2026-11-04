"""python -m itpops.sentinel —— 调试别名，转发到唯一入口 `itpops.cli.main`。

正式入口是 `itp-ops.sh sentinel`（等价 `python -m itpops sentinel`）。
与 `itpops.buildx` 同理：不直接调特性实现，避免绕过全局参数 / JSON 信封 / 退出码契约。
"""
from __future__ import annotations

import sys

from ..cli.main import main

if __name__ == "__main__":
    sys.exit(main(["sentinel", *sys.argv[1:]]))
