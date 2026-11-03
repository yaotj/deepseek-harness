#!/usr/bin/env python3
# ============================================================================
# web 前端（CloudOS 应用包 web-frontend）一键发版 —— 薄壳（v2）
#
# 实现收编进 itpops 包：itpops/web/frontend_release.py（原脚本全文见
# web_frontend_release.legacy.py）。命令面与旧版逐字兼容；新增可选 --env。
# 用法：
#   python3 web_frontend_release.py <版本> [--yes] [--skip-build] [--skip-upgrade] ...
#   python3 web_frontend_release.py --bump|--debug [--yes] ...
#   不带 --yes = dry-run（本地出 zip + 打印将发的请求，不碰生产）
# ============================================================================
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itpops.web.frontend_release import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
