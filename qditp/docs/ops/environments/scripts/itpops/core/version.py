"""升级版本号规则（前端/后端统一，2026-11-02 裁定）—— lib/version.sh 的 Python 移植。

【规则（用户裁定，全平台唯一口径）】
  1. 本地构建只升末位 +0.0.1：即「最后一分段 +1」。
  2. 任一分段超过 99 则向高位进位：该分段归零、前一分段 +1
     （语义："超过 99 则升 0.1.0"）。例：1.2.99 → 1.3.0；1.99.99 → 2.0.0。
  3. CloudOS 中仅为调试而升级的包，版本固定哨兵 0.0.0.1（不占用发布序列）。

【与 bash 版的语义差异（仅病态输入）】
  · 分段含前导零（如 1.010）：bash 3.2 算术把 010 当八进制（$((010+1))=9），
    本实现按十进制（→ 1.11）。前导零版本号本就非法使用，保留十进制语义。
  · 未被 +1 触及的分段保留原字符串（含前导零），与 bash 一致。
"""
from __future__ import annotations

import re

from ..errors import EXIT_USAGE, OpsError

# 仅数字与点、至少 1 段、无前导/尾随/连续点 —— 与 bash case glob 逐条等价：
#   ""|.|*.*.|*.[!0-9]*|*[!0-9.]*|.*|.  全部拒绝 ⇔ 只接受 ^\d+(\.\d+)*$
_VALID_RE = re.compile(r"^\d+(?:\.\d+)*$")

DEBUG_VERSION = "0.0.0.1"
SEGMENT_LIMIT = 99


def version_valid(v: str) -> bool:
    """校验版本号：仅含数字与点，至少 1 段；无前导/尾随/连续点，无非法字符。"""
    return bool(_VALID_RE.match(v))


def next_version(v: str) -> str:
    """末位 +1，段上限 99，超限向高位进位（该段归零、前段 +1）。

    最高段仍溢出（如 99.99）：补一个最高段 1，其余已归零 → 1.0.0。
    非法版本号抛 OpsError(EXIT_USAGE)，与 bash 版 stderr「非法版本号: x」同文案。
    """
    if not version_valid(v):
        raise OpsError(f"非法版本号: {v}", EXIT_USAGE)

    segs = v.split(".")
    out: list[str] = []
    carry = 1
    for seg in reversed(segs):
        if carry:
            s = int(seg) + 1
            if s > SEGMENT_LIMIT:
                seg_s, carry = "0", 1
            else:
                seg_s, carry = str(s), 0
        else:
            seg_s = seg  # 未触及的分段原样保留（含前导零），与 bash 一致
        out.append(seg_s)
    out.reverse()
    if carry:  # 最高段仍溢出：补一个最高段 1
        out.insert(0, "1")
    return ".".join(out)


def debug_version() -> str:
    """调试包哨兵版本（CloudOS 仅为调试而升级时使用，不占用发布序列）。"""
    return DEBUG_VERSION
