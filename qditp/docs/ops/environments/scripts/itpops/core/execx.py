"""本地执行 / 写操作门 / 依赖检查 / 仓库根 —— lib/core.sh 对应能力。

纪律（与旧 lib 一致）：
  · 写操作默认拒绝，必须显式 --yes（与旧脚本逐条对齐，不新增、不削弱）
  · 失败抛 OpsError（细分退出码），NEVER 静默继续
"""
from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from ..errors import EXIT_NEED_YES, EXIT_PRECONDITION, EXIT_TRANSPORT, OpsError
from .ui import context

# 仓库根推导：本文件在 <repo>/docs/ops/environments/scripts/itpops/core/；
# scripts 目录 = parents[2]，再上 4 层到仓库根（与 bash 版 ITP_OPS_ROOT 同一口径）。
SCRIPTS_DIR = Path(__file__).resolve().parents[2]
REPO_ROOT_CANDIDATE = SCRIPTS_DIR.parents[3]


def repo_root() -> Path:
    """返回仓库根；用标志目录断言（cd 的 .. 可无限叠加，深度写错不报错，故必须断言）。"""
    root = REPO_ROOT_CANDIDATE
    if not (root / "account-server").is_dir():
        raise OpsError(
            f"仓库根推导错（{root}），itp-ops 可能被移动了位置", EXIT_PRECONDITION
        )
    return root


def scripts_dir() -> Path:
    return SCRIPTS_DIR


def require_yes(action: str) -> None:
    """写操作门：缺 --yes 即拒（生产脚本的既有行为，逐条保留）。"""
    if not context().yes:
        raise OpsError(f"这是写操作：{action}。确认无误请加 --yes", EXIT_NEED_YES)


def need_cmd(*cmds: str) -> None:
    """外部依赖检查：缺什么一次说清。"""
    missing = [c for c in cmds if shutil.which(c) is None]
    if missing:
        raise OpsError(f"缺少命令：{' '.join(missing)}", EXIT_PRECONDITION)


def run(args: list[str], *, what: str = "", check: bool = True) -> subprocess.CompletedProcess:
    """本机执行（list 形参，不经 shell）。非 0 且 check → EXIT_EXEC。"""
    try:
        r = subprocess.run(args, capture_output=True, text=True)
    except OSError as exc:
        raise OpsError(f"执行失败：{exc}", EXIT_TRANSPORT) from exc
    if check and r.returncode != 0:
        detail = (r.stderr or r.stdout or "").strip().splitlines()
        tail = " | ".join(detail[-3:]) if detail else f"exit {r.returncode}"
        raise OpsError(f"命令失败（{what or ' '.join(args)}）：{tail}", EXIT_EXEC)
    return r
