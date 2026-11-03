"""python -m itpops.sentinel —— 直接调用（调试/脚本用；正式入口是 `itp-ops.sh sentinel`）。"""
import sys

from .core import main

if __name__ == "__main__":
    sys.exit(main())
