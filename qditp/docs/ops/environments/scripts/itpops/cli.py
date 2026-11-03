"""itpops CLI —— 全局参数解析 + 域分发（itp-ops.sh v1 的 Python 对应层）。

【双受众契约】
  人：itp-ops.sh <域> …          人机文本：结果走 stdout，日志/横幅走 stderr，TTY 才着色
  大模型：itp-ops.sh --json …    stdout 只输出一条 JSON 信封：
      {"ok": true,  "command": [...], "data": <ui.result 登记的载荷>, "error": null}
      {"ok": false, "command": [...], "data": null, "error": {"code","message","exit"}}
  自描述：itp-ops.sh catalog --json 拿全量命令注册表。

【域契约】itpops.domains.<mod> 暴露 main(args: list[str]) -> int 与 CATALOG。
  成功路径：ui.out() 输出人看文本 + ui.result() 登记机器载荷 → 返回 0。
  失败路径：raise OpsError(message, exit_code)。

【全局参数（可出现在任意位置，入口负责剥离，域只收到业务参数）】
  --env hongdao|test / --env=…  环境切换；各域默认值刻意不统一（见 catalog）
  --yes                         确认写操作
  --json                        机器输出信封
  -h/--help、--version          仅域前有效；域后的交给域自己解释
"""
from __future__ import annotations

import importlib
import json
import sys

from . import ITP_OPS_VERSION
from . import catalog as catalog_mod
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
    "catalog": "catalog",
    "cloudos": "cloudos",
    "k8s": "k8s",
    "log": "log",
    "diag": "diag",
    "analysis": "analysis",
}


def _usage() -> str:
    return f"""\
itp-ops v{ITP_OPS_VERSION} —— 青岛地铁 ITP 平台运维单一入口

用法: itp-ops.sh [全局参数...] <域> [域参数...]
      itp-ops.sh help
      itp-ops.sh catalog [--json]     # 自描述命令注册表（大模型入口）

全局参数（可出现在任意位置）
  --env hongdao|test   环境切换（全局，所有域生效）。未指定时用各域自己的默认：
                       cloudos=hongdao、k8s/log=test、diag 按子命令（reach=hongdao、routes=test）
                       默认值刻意不统一——NEVER 擅自改齐，那会让原可用的命令失效；
                       每个域运行前都会打印「[环境]」横幅，明确当前打的是哪个环境
  --yes                确认写操作；缺它写命令一律拒绝
  --json               stdout 输出单条 JSON 信封 {{ok, command, data, error}}（大模型用）
  -h | --help          总帮助（仅域前）
  --version           版本号（仅域前）

域一览
  cloudos    云管台运维：应用列表/env/实例/日志/副本伸缩/镜像构建…
            itp-ops.sh [--env hongdao|test] cloudos <命令> [参数]
            itp-ops.sh cloudos health            # 连通性自检，写操作前必跑
            itp-ops.sh cloudos apps Error        # 只列异常态应用
            itp-ops.sh cloudos env fep-app       # env 全量（排 RPC 地址错配首选）
            itp-ops.sh cloudos scale fep-app 2 --yes
  k8s       镜像构建推送（本机 jar → k8s-master → Harbor；**只对测试环境可用**，
            红岛发版走 cloudos image）
            itp-ops.sh k8s deploy fep-dev-server --yes
            itp-ops.sh k8s deploy-all --yes      # 有序全量，失败即断
  log       日志查看 / 重启自动重挂监控
            itp-ops.sh log tail fep-app -f       # 实时跟踪
            itp-ops.sh log watch                 # 监控守护（Pod 重启自动重挂）
  diag      网络可达性自检 / 入向路由导出
            itp-ops.sh diag reach                # 红岛分区可达性
            itp-ops.sh diag routes               # 48000 入向路由全链路导出
  analysis  静态分析（端点计数 / 接口清单 / 需求提取）
            itp-ops.sh analysis endpoints account-server/src/main/java
  version   升级版本号计算（前端/后端统一规则）：next <当前版本> 求下一版、debug 求调试哨兵
            itp-ops.sh version next 1.2.42   # → 1.2.43
            itp-ops.sh version debug         # → 0.0.0.1

每个域的详细用法：itp-ops.sh <域> help
版本算法真源：itpops/core/version.py（本包）"""


def _emit_json(ok: bool, command: list[str], data: object, error: dict | None) -> None:
    print(json.dumps(
        {"ok": ok, "command": command, "data": data, "error": error},
        ensure_ascii=False,
    ))


def _dispatch(domain: str, args: list[str]) -> int:
    module_name = _DOMAIN_MODULES.get(domain)
    if module_name is None:
        raise OpsError(f"未知域：{domain}", EXIT_USAGE)
    try:
        mod = importlib.import_module(f".domains.{module_name}", package="itpops") \
            if module_name != "catalog" else importlib.import_module(".catalog", package="itpops")
    except ImportError as exc:
        raise OpsError(
            f"域尚未移植到 Python：{domain}（迁移期可暂用 itp-ops.legacy.sh）"
            f"（{exc}）",
            EXIT_PRECONDITION,
        ) from exc
    return mod.main(args)


def main(argv: list[str] | None = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    ctx = Ctx(command=argv)

    # ---- 全局参数扫描（镜像 itp-ops.sh 的 while 分发：域前剥离，域后原样传递）----
    domain = ""
    domain_args: list[str] = []
    rest = list(argv)
    while rest:
        a = rest[0]
        if a == "--env":
            if len(rest) < 2:
                ui.log_error("错误：--env 缺值（hongdao|test）")
                return EXIT_USAGE
            ctx.env = rest[1]
            ctx.env_explicit = True
            rest = rest[2:]
        elif a.startswith("--env="):
            ctx.env = a[len("--env="):]
            ctx.env_explicit = True
            rest = rest[1:]
        elif a == "--yes":
            ctx.yes = True
            rest = rest[1:]
        elif a == "--json":
            ctx.json_mode = True
            rest = rest[1:]
        elif a in ("-h", "--help"):
            if not domain:
                ui.out(_usage())
                return EXIT_OK
            domain_args.append(a)
            rest = rest[1:]
        elif a == "--version":
            if not domain:
                ui.out(f"itp-ops {ITP_OPS_VERSION}")
                return EXIT_OK
            domain_args.append(a)
            rest = rest[1:]
        else:
            if not domain:
                domain = a
            else:
                domain_args.append(a)
            rest = rest[1:]

    set_context(ctx)

    # ---- 域分发 ----
    try:
        if domain in ("", "help"):
            ui.out(_usage())
            return EXIT_OK

        rc = _dispatch(domain, domain_args)

        if ctx.json_mode:
            _emit_json(True, argv, ui.get_result(), None)
        return rc if isinstance(rc, int) else EXIT_OK

    except OpsError as exc:
        if ctx.json_mode:
            _emit_json(False, argv, None, exc.to_json())
        else:
            ui.log_error(f"错误：{exc}")
            if exc.exit_code == EXIT_USAGE:
                # 与 v1 一致：未知域/子命令给出用法提示
                print(_usage(), file=sys.stderr)
        return exc.exit_code
    except KeyboardInterrupt:
        ui.log_error("已中断")
        return 130
    except Exception as exc:  # noqa: BLE001 —— 兜底也走信封，别让大模型拿到裸 traceback
        if ctx.json_mode:
            _emit_json(False, argv, None, {
                "code": "GENERIC", "message": f"{type(exc).__name__}: {exc}", "exit": EXIT_GENERIC,
            })
        else:
            ui.log_error(f"未预期异常：{type(exc).__name__}: {exc}")
        return EXIT_GENERIC


if __name__ == "__main__":
    sys.exit(main())
