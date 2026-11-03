"""单测公共工具：以子进程方式跑 `python3 -m itpops`（与真实入口同一条路径）。

用 stdlib unittest —— 本目录刻意零第三方依赖，`python3 -m unittest discover -s tests` 即可。
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1]


def run_cli(*args: str) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    env["PYTHONPATH"] = str(SCRIPTS_DIR) + (
        os.pathsep + env["PYTHONPATH"] if env.get("PYTHONPATH") else ""
    )
    env.pop("ITP_OPS_ENV", None)
    return subprocess.run(
        [sys.executable, "-m", "itpops", *args],
        capture_output=True, text=True, env=env, cwd=str(SCRIPTS_DIR),
    )


def run_json(*args: str) -> tuple[dict, int]:
    r = run_cli("-o", "json", *args)
    try:
        payload = json.loads(r.stdout)
    except json.JSONDecodeError:  # pragma: no cover - 出错时给出可读信息
        raise AssertionError(f"stdout 不是 JSON：{r.stdout!r} / stderr={r.stderr!r}")
    return payload, r.returncode
