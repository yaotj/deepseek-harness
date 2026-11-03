"""人机输出原语。

纪律（与旧 lib/core.sh 一致）：
  · 人看的信息（INFO/WARN/ERROR/STEP/横幅）一律 **stderr**，stdout 留给结果与 --json；
  · 仅 TTY 上色，重定向/管道时纯文本（便于 grep 与日志归档）；
  · --json 模式下人机文本仍走 stderr，stdout 只输出一条 JSON 信封（由 cli 负责）。
"""
from __future__ import annotations

import sys
from dataclasses import dataclass, field


@dataclass
class Ctx:
    """一次执行的全局上下文（入口解析全局参数后构造，域代码只读）。"""

    env: str | None = None            # --env 显式值；None = 用各域默认
    env_explicit: bool = False
    yes: bool = False                 # --yes：确认写操作
    json_mode: bool = False           # --json：stdout 输出机器信封
    command: list[str] = field(default_factory=list)  # 原始命令行（回显进 JSON）


_CTX = Ctx()
_RESULT: object = None


def set_context(ctx: Ctx) -> None:
    global _CTX
    _CTX = ctx


def context() -> Ctx:
    return _CTX


def result(payload: object) -> None:
    """登记机器可读结果（--json 信封的 data 字段）。

    契约：域在成功路径末尾调用一次；人看文本照常经 out() 输出。
    --json 模式下 stdout 只有 cli 打的一条信封，out() 被抑制。
    """
    global _RESULT
    _RESULT = payload


def get_result() -> object:
    return _RESULT


class _Color:
    def __init__(self) -> None:
        self.enabled = sys.stderr.isatty()

    def wrap(self, code: str, text: str) -> str:
        return f"\033[{code}m{text}\033[0m" if self.enabled else text


_C = _Color()


def log_info(msg: str) -> None:
    print(f"{_C.wrap('0;32', '[INFO]')} {msg}", file=sys.stderr)


def log_warn(msg: str) -> None:
    print(f"{_C.wrap('1;33', '[WARN]')} {msg}", file=sys.stderr)


def log_error(msg: str) -> None:
    print(f"{_C.wrap('0;31', '[ERROR]')} {msg}", file=sys.stderr)


def log_step(msg: str) -> None:
    print(f"{_C.wrap('0;36', '[STEP]')} {msg}", file=sys.stderr)


def out(text: str = "") -> None:
    """结果输出（人看的最终结果）→ stdout。

    --json 模式下抑制（stdout 留给单条 JSON 信封，机器数据走 result()）；
    人机日志不受影响，仍走 stderr。
    """
    if _CTX.json_mode:
        return
    print(text)


def die(msg: str) -> None:
    """旧 core.sh 的 die：报错并以退出码 1 终止（新代码请 raise OpsError 拿细分码）。"""
    print(f"{_C.wrap('0;31', '错误：')}{msg}", file=sys.stderr)
    sys.exit(1)
