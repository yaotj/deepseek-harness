"""人机输出原语。

纪律（对齐成熟 CLI：stdout 只放数据，诊断信息走 stderr）：
  · INFO/WARN/ERROR/STEP/DEBUG/环境横幅一律 **stderr**，stdout 留给结果与 -o json；
  · 仅 TTY 上色；NO_COLOR 置值即关色，CLICOLOR_FORCE=1 强制开色，--no-color 优先级最高；
  · -q/--quiet 抑制 INFO/STEP（WARN/ERROR 永远保留）；-v/--verbose 才出 DEBUG；
  · -o json（--json 同义）模式下 stdout 只有一条 JSON 信封（cli 负责），out() 自动抑制。
"""
from __future__ import annotations

import os
import sys
from dataclasses import dataclass, field


@dataclass
class Ctx:
    """一次执行的全局上下文（入口解析全局参数后构造，域代码只读）。"""

    env: str | None = None            # --env 显式值；None = 用各域默认
    env_explicit: bool = False
    yes: bool = False                 # --yes / -y：确认写操作
    dry_run: bool = False             # -n / --dry-run：强制预演（优先于 --yes）
    json_mode: bool = False           # -o json / --json：stdout 输出机器信封
    output: str = "text"              # text | json
    quiet: bool = False               # -q：抑制 INFO/STEP
    verbose: int = 0                  # -v 可叠加：出 DEBUG（ssh 命令行等）
    no_color: bool = False            # --no-color
    trace: bool = False               # --trace：异常打印完整堆栈
    command: list[str] = field(default_factory=list)  # 原始命令行（回显进 JSON）


_CTX = Ctx()
_RESULT: object = None


def set_context(ctx: Ctx) -> None:
    global _CTX
    _CTX = ctx


def context() -> Ctx:
    return _CTX


def result(payload: object) -> None:
    """登记机器可读结果（-o json 信封的 data 字段）。

    契约：域在成功路径末尾调用一次；人看文本照常经 out() 输出。
    """
    global _RESULT
    _RESULT = payload


def get_result() -> object:
    return _RESULT


def color_enabled() -> bool:
    """上色判定优先级：--no-color > NO_COLOR > CLICOLOR_FORCE > TTY。"""
    if _CTX.no_color:
        return False
    if os.environ.get("NO_COLOR"):
        return False
    force = os.environ.get("CLICOLOR_FORCE", "")
    if force and force != "0":
        return True
    return sys.stderr.isatty()


def _wrap(code: str, text: str) -> str:
    return f"\033[{code}m{text}\033[0m" if color_enabled() else text


def log_info(msg: str) -> None:
    if _CTX.quiet:
        return
    print(f"{_wrap('0;32', '[INFO]')} {msg}", file=sys.stderr)


def log_warn(msg: str) -> None:
    print(f"{_wrap('1;33', '[WARN]')} {msg}", file=sys.stderr)


def log_error(msg: str) -> None:
    print(f"{_wrap('0;31', '[ERROR]')} {msg}", file=sys.stderr)


def log_step(msg: str) -> None:
    if _CTX.quiet:
        return
    print(f"{_wrap('0;36', '[STEP]')} {msg}", file=sys.stderr)


def debug(msg: str) -> None:
    """-v 才输出的调试信息（远端命令行、探活细节等；NEVER 打印密钥明文）。"""
    if not _CTX.verbose:
        return
    print(f"{_wrap('0;90', '[DEBUG]')} {msg}", file=sys.stderr)


def out(text: str = "") -> None:
    """结果输出（人看的最终结果）→ stdout。

    -o json 模式下抑制（stdout 留给单条 JSON 信封，机器数据走 result()）；
    人机日志不受影响，仍走 stderr。
    """
    if _CTX.json_mode:
        return
    print(text)


def die(msg: str) -> None:
    """旧 core.sh 的 die：报错并以退出码 1 终止（新代码请 raise OpsError 拿细分码）。"""
    print(f"{_wrap('0;31', '错误：')}{msg}", file=sys.stderr)
    sys.exit(1)
