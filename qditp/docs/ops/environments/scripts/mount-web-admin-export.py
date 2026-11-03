#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把测试环境 web-admin 的 app-logs-v2 PVC 挂到 /home/javaapp/app/export/ —— 薄壳（v2）。

实现收编进 itpops 包：itpops/web/mount_export.py（原脚本全文见
mount-web-admin-export.legacy.py）。命令面与旧版兼容：--yes 下发、缺省预演。
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itpops.web.mount_export import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
