"""输入纠错建议 —— 拼错域 / 子命令 / 参数时给出最接近的候选。

只增强人看的报错文本，不改变退出码；`-o json` 时建议文本进 error.message，
机器仍按 error.code / error.exit 判断。用 SequenceMatcher 而非 get_close_matches，
因为候选带 `--` 前缀，需要先剥掉再比，同时保留原样显示。
"""
from __future__ import annotations

from difflib import SequenceMatcher
from typing import Iterable


def closest(word: str, candidates: Iterable[str], *, n: int = 3, cutoff: float = 0.55) -> list[str]:
    key = str(word).lstrip("-").lower()
    scored: list[tuple[float, str]] = []
    for cand in dict.fromkeys(c for c in candidates if c):
        ck = cand.lstrip("-").lower()
        ratio = SequenceMatcher(None, key, ck).ratio()
        if key and (ck.startswith(key[:3]) or key.startswith(ck[:3])):
            ratio = max(ratio, 0.72)          # 共同前缀给一点加成，便于 --evn → --env
        if ratio >= cutoff:
            scored.append((ratio, cand))
    scored.sort(key=lambda x: (-x[0], x[1]))
    return [c for _, c in scored[:n]]


def append_hint(message: str, word: str, candidates: Iterable[str]) -> str:
    """把「你是不是想找：…」追加到报错消息（无相近候选时原样返回）。"""
    matches = closest(word, candidates)
    if not matches:
        return message
    return message + "\n  你是不是想找：" + "、".join(matches)
