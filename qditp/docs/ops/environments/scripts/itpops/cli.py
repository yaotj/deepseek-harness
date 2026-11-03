"""itpops CLI —— 全局参数解析 + 域分发（对齐 kubectl / gh / git 的用法约定）。

【用法】
  itp-ops.sh [全局参数] <域> <命令> [参数...]
  itp-ops.sh <域> help                 域内命令总览（域自带富文本）
  itp-ops.sh <域> <命令> -h            单命令帮助（由 CATALOG 生成）
  itp-ops.sh catalog [-o json]         机器可读命令注册表
  itp-ops.sh completion <bash|zsh>     shell 补全脚本

【全局参数（可出现在任意位置，入口剥离，域只收到业务参数）】
  -e, --env hongdao|test   环境（默认因域而异：cloudos=hongdao，k8s/log=test…）
  -y, --yes                确认写操作（缺它与 -n 时写命令拒绝执行）
  -n, --dry-run            强制预演（等价于不带 --yes；优先于 --yes）
  -o, --output text|json   输出格式（--json 是 -o json 的简写，保留兼容）
  -q, --quiet              抑制 INFO/STEP
  -v, --verbose            DEBUG 输出（远端命令行等），可叠加 -vv
      --no-color           关色（等价 NO_COLOR）
      --trace              异常打印完整堆栈
  -h, --help               帮助（任意层级都有效）
  -V, --version            版本
  --                       之后的参数一律按位置参数处理（值以 - 开头时用）

【双受众契约】
  人：结果 → stdout；日志/横幅 → stderr；TTY 才着色
  大模型：-o json → stdout 单条信封 {ok, command, data, error, duration_ms}
          catalog -o json → 全量命令注册表；退出码 0/2/3/4/5/6/130 稳定

【域契约】itpops.domains.<mod> 暴露 main(args) -> int 与 CATALOG。
  成功：ui.out() 人看文本 + ui.result() 机器载荷 → 0；失败：raise OpsError。
"""
from __future__ import annotations

import importlib
import json
import os
import subprocess
import sys
import time
import traceback

from . import ITP_OPS_VERSION
from . import completion as completion_mod
from . import help as help_mod
from .core import ui
from .core.ui import Ctx, set_context
from .errors import (
    EXIT_GENERIC,
    EXIT_OK,
    EXIT_PRECONDITION,
    EXIT_USAGE,
    OpsError,
)

# 域名 → itpops.domains 模块名（迁移期各域逐个就位）
_DOMAIN_MODULES = {
    "version": "version_cmd",
    "cloudos": "cloudos",
    "k8s": "k8s",
    "log": "log",
    "diag": "diag",
    "analysis": "analysis",
}

_OUTPUT_CHOICES = ("text", "json")


# ---------------------------------------------------------------- 版本与帮助
def _git_sha() -> str:
    """尽力取仓库短 sha（取不到就空，不阻断 --version）。"""
    try:
        from .core.execx import repo_root

        r = subprocess.run(
            ["git", "-C", str(repo_root()), "rev-parse", "--short", "HEAD"],
            capture_output=True, text=True, timeout=3,
        )
        return r.stdout.strip() if r.returncode == 0 else ""
    except Exception:  # noqa: BLE001 —— 非仓库/无 git 时静默
        return ""


def _version_text() -> str:
    sha = _git_sha()
    lines = [f"itp-ops version {ITP_OPS_VERSION}"]
    lines.append(f"  python {sys.version.split()[0]}  ({sys.executable})")
    if sha:
        lines.append(f"  commit {sha}")
    return "\n".join(lines)


def _root_help() -> str:
    doms = [
        ("cloudos", "云管台运维：apps/env/pods/scale/deploy/image…", "默认 --env hongdao"),
        ("k8s", "镜像构建推送（仅测试环境可用）", "默认 --env test"),
        ("log", "日志查看 / Pod 重启自动重挂监控", "默认 --env test"),
        ("diag", "网络可达性自检 / 48000 入向路由导出", "reach=hongdao、routes=test"),
        ("analysis", "静态分析：端点计数", "环境无关"),
        ("version", "版本号计算：next / debug", "本地计算"),
        ("catalog", "自描述命令注册表（大模型入口）", "-o json 可读"),
        ("completion", "shell 补全脚本（bash / zsh）", ""),
    ]
    dom_lines = [
        f"  {n:<11} {s}" + (f"    （{e}）" if e else "") for n, s, e in doms
    ]
    return f"""\
itp-ops {ITP_OPS_VERSION} —— 青岛地铁 ITP 平台运维单一入口

用法:
  itp-ops.sh [全局参数] <域> <命令> [参数...]
  itp-ops.sh <域> help                  域内命令总览
  itp-ops.sh <域> <命令> -h             单命令帮助
  itp-ops.sh catalog [-o json]          机器可读命令注册表
  itp-ops.sh completion <bash|zsh>      shell 补全脚本

全局参数:
  -e, --env hongdao|test    环境；各域默认值刻意不统一（见下），未指定时用域默认
  -y, --yes                 确认写操作；缺它写命令拒绝执行
  -n, --dry-run             强制预演（等价不带 --yes，优先于 --yes；无预演分支的命令会报错）
  -o, --output text|json    输出格式；--json 为 -o json 的简写
  -q, --quiet               抑制 INFO/STEP 日志
  -v, --verbose             DEBUG 输出（远端命令行等），可叠加
      --no-color            关闭颜色（亦可 export NO_COLOR=1）
      --trace               出错时打印完整堆栈
  -h, --help                帮助（任意层级有效）
  -V, --version             版本信息
  --                        其后的参数按位置参数处理

域:
{chr(10).join(dom_lines)}

示例:
  itp-ops.sh cloudos health                        # 写操作前必跑的连通性自检
  itp-ops.sh cloudos apps Error                    # 只列异常态应用
  itp-ops.sh --env test k8s deploy fep-dev-server --yes
  itp-ops.sh -o json cloudos apps Error            # 机器信封，可接 jq
  itp-ops.sh log tail fep-app -f                   # 实时跟踪
  itp-ops.sh version next 1.2.42                   # → 1.2.43

退出码:
  0 成功   2 用法错误   3 前置不满足   4 远端/传输失败   5 执行失败   6 缺 --yes   130 中断

环境变量:
  NO_COLOR / CLICOLOR_FORCE     颜色开关
  ITP_OPS_ENV                   默认环境（等价 --env；显式 --env 优先）

更多: itp-ops.sh catalog -o json（全量命令面）"""


# ---------------------------------------------------------------- 输出
def _emit_json(ok: bool, command: list[str], data: object, error: dict | None,
               duration_ms: int) -> None:
    print(json.dumps(
        {"ok": ok, "command": command, "data": data, "error": error,
         "duration_ms": duration_ms},
        ensure_ascii=False,
    ))


def _fail(ctx: Ctx, argv: list[str], exc: OpsError, t0: float) -> int:
    elapsed = int((time.monotonic() - t0) * 1000)
    if ctx.json_mode:
        _emit_json(False, argv, None, exc.to_json(), elapsed)
    else:
        ui.log_error(f"错误：{exc}")
        if exc.exit_code == EXIT_USAGE:
            print("提示：itp-ops.sh -h 查看总帮助；<域> help / <域> <命令> -h 查看细分帮助",
                  file=sys.stderr)
        if ctx.trace:
            traceback.print_exc()
    return exc.exit_code


# ---------------------------------------------------------------- 域分发
def _dispatch(domain: str, args: list[str]) -> int:
    if domain == "completion":
        return _completion(args)
    if domain == "help":
        return _help_command(args)
    if domain == "catalog":
        from . import catalog as catalog_mod

        return catalog_mod.main(args)
    module_name = _DOMAIN_MODULES.get(domain)
    if module_name is None:
        raise OpsError(f"未知域：{domain}", EXIT_USAGE)
    try:
        mod = importlib.import_module(f".domains.{module_name}", package="itpops")
    except ImportError as exc:
        raise OpsError(f"域尚未移植到 Python：{domain}（{exc}）", EXIT_PRECONDITION) from exc
    return mod.main(args)


def _completion(args: list[str]) -> int:
    shell = args[0] if args else ""
    if shell in ("-h", "--help", "help", ""):
        ui.out("用法: itp-ops.sh completion <bash|zsh>    # 输出补全脚本（source 或放进补全目录）")
        return 0 if shell in ("-h", "--help", "help", "") else EXIT_USAGE
    if shell == "bash":
        ui.out(completion_mod.bash_script())
        return 0
    if shell == "zsh":
        ui.out(completion_mod.zsh_script())
        return 0
    raise OpsError(f"不支持的 shell：{shell}（仅 bash / zsh）", EXIT_USAGE)


def _help_command(args: list[str]) -> int:
    if not args:
        ui.out(_root_help())
        return 0
    domain, rest = args[0], args[1:]
    if rest:
        if help_mod.maybe_handle_help(domain, [*rest, "-h"]):
            return 0
    return _dispatch(domain, ["help"])


# ---------------------------------------------------------------- 参数解析
def _parse_globals(argv: list[str], ctx: Ctx) -> tuple[str, list[str], bool, bool]:
    """扫描全局参数；返回 (域, 域参数, help请求, version请求)。"""
    domain = ""
    domain_args: list[str] = []
    want_help = False
    want_version = False
    rest = list(argv)
    i = 0
    literal = False  # `--` 之后一律按位置参数
    while i < len(rest):
        a = rest[i]
        if literal:
            if not domain:
                domain = a
            else:
                domain_args.append(a)
            i += 1
            continue
        if a == "--":
            literal = True
            i += 1
            continue
        if a.startswith("--env=") or a.startswith("-e="):
            ctx.env, ctx.env_explicit = a.split("=", 1)[1], True
            i += 1
            continue
        if a in ("--env", "-e"):
            if i + 1 >= len(rest):
                raise OpsError("--env 缺值（hongdao|test）", EXIT_USAGE)
            ctx.env, ctx.env_explicit = rest[i + 1], True
            i += 2
            continue
        if a in ("--yes", "-y"):
            ctx.yes = True
            i += 1
            continue
        if a in ("--dry-run", "-n"):
            ctx.dry_run = True
            i += 1
            continue
        if a == "--json":
            ctx.output, ctx.json_mode = "json", True
            i += 1
            continue
        if a.startswith("--output=") or a.startswith("-o="):
            val = a.split("=", 1)[1]
            if val not in _OUTPUT_CHOICES:
                raise OpsError(
                    f"--output 只支持 {'/'.join(_OUTPUT_CHOICES)}，收到：{val}", EXIT_USAGE
                )
            ctx.output, ctx.json_mode = val, val == "json"
            i += 1
            continue
        if a in ("--output", "-o"):
            if i + 1 >= len(rest):
                raise OpsError("--output 缺值（text|json）", EXIT_USAGE)
            val = rest[i + 1]
            if val not in _OUTPUT_CHOICES:
                raise OpsError(
                    f"--output 只支持 {'/'.join(_OUTPUT_CHOICES)}，收到：{val}", EXIT_USAGE
                )
            ctx.output, ctx.json_mode = val, val == "json"
            i += 2
            continue
        if a in ("--quiet", "-q"):
            ctx.quiet = True
            i += 1
            continue
        if a == "--verbose":
            ctx.verbose += 1
            i += 1
            continue
        if a.startswith("-") and a != "-" and set(a[1:]) == {"v"}:
            ctx.verbose += len(a) - 1  # -v / -vv / -vvv
            i += 1
            continue
        if a == "--no-color":
            ctx.no_color = True
            i += 1
            continue
        if a == "--trace":
            ctx.trace = True
            i += 1
            continue
        if a in ("-h", "--help"):
            if not domain:
                want_help = True
            else:
                domain_args.append(a)
            i += 1
            continue
        if a in ("-V", "--version"):
            if not domain:
                want_version = True
            else:
                domain_args.append(a)
            i += 1
            continue
        # 位置参数
        if not domain:
            if a.startswith("-") and a != "-":
                raise OpsError(f"未知参数：{a}（itp-ops.sh -h 查看用法）", EXIT_USAGE)
            domain = a
        else:
            domain_args.append(a)
        i += 1
    return domain, domain_args, want_help, want_version


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    ctx = Ctx(command=argv)
    t0 = time.monotonic()

    try:
        # ITP_OPS_ENV 环境变量：等价 --env（显式 --env 优先）
        if not ctx.env and os.environ.get("ITP_OPS_ENV"):
            ctx.env, ctx.env_explicit = os.environ["ITP_OPS_ENV"], True

        domain, domain_args, want_help, want_version = _parse_globals(argv, ctx)
        # --dry-run 优先于 --yes
        if ctx.dry_run:
            ctx.yes = False
        set_context(ctx)

        if want_version:
            ui.out(_version_text())
            return EXIT_OK
        if want_help or not domain:
            ui.out(_root_help())
            return EXIT_OK

        # 命令级 -h：能由 CATALOG 出帮助就本地渲染（绝不把 -h 当业务参数执行）
        if any(a in ("-h", "--help") for a in domain_args):
            if help_mod.maybe_handle_help(domain, domain_args):
                return EXIT_OK
            if not domain_args or all(a.startswith("-") for a in domain_args):
                domain_args = ["help"]

        rc = _dispatch(domain, domain_args)
        if ctx.json_mode:
            _emit_json(True, argv, ui.get_result(), None,
                       int((time.monotonic() - t0) * 1000))
        return rc if isinstance(rc, int) else EXIT_OK

    except OpsError as exc:
        return _fail(ctx, argv, exc, t0)
    except KeyboardInterrupt:
        if ctx.json_mode:
            _emit_json(False, argv, None,
                       {"code": "INTERRUPTED", "message": "已中断", "exit": 130},
                       int((time.monotonic() - t0) * 1000))
        else:
            ui.log_error("已中断")
        return 130
    except Exception as exc:  # noqa: BLE001 —— 兜底也走信封，别让大模型拿到裸 traceback
        if ctx.json_mode:
            _emit_json(False, argv, None,
                       {"code": "GENERIC", "message": f"{type(exc).__name__}: {exc}",
                        "exit": EXIT_GENERIC},
                       int((time.monotonic() - t0) * 1000))
        else:
            ui.log_error(f"未预期异常：{type(exc).__name__}: {exc}")
            if ctx.trace:
                traceback.print_exc()
        return EXIT_GENERIC


if __name__ == "__main__":
    sys.exit(main())
