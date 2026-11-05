"""交互式发布向导 —— 问答式拼出完整命令，NEVER 执行任何写操作。

设计边界：
  · 只在 TTY 且非 -o json 时提问；管道 / 大模型 / 重定向一律降级为「命令模板」，
    不会挂住 stdin，也不会污染 JSON 信封；
  · 生成的命令**默认不含 --yes**（只读预演）；执行仍需用户自己加 --yes，
    生产另需 --allow-prod，绝不绕过写操作门；
  · 只调用 definitions.build() 取步骤清单用于展示，不调用 runner.run()。
"""
from __future__ import annotations

import signal
import sys

from ..core import ui
from ..errors import EXIT_USAGE, OpsError
from . import definitions

_CHOICES = (
    ("backend", "后端一键发布（模块 → jar → 镜像 → 切镜像）"),
    ("frontend", "前端一键发布（zip → 软件中心 → 升级）"),
    ("jvm", "JVM 三件套升级（IT-020）"),
)

_TEMPLATES = (
    "./itp-ops.sh flow backend <模块目录> --env test --yes      # 后端发布",
    "./itp-ops.sh flow frontend --env test --yes                # 前端发布",
    "./itp-ops.sh flow jvm <应用> --env test --yes             # JVM 参数升级",
)


def _ask(prompt: str, default: str = "", *, timeout: int = 60) -> str:
    """读一行；非 TTY / 无输入 / 超时都不阻塞，统一转成 OpsError。"""
    suffix = " [%s]: " % default if default else ": "

    def _expired(signum, frame):
        raise TimeoutError()

    has_alarm = hasattr(signal, "SIGALRM")
    old = signal.signal(signal.SIGALRM, _expired) if has_alarm else None
    if has_alarm:
        signal.alarm(timeout)
    try:
        answer = input(prompt + suffix).strip()
    except (EOFError, TimeoutError):
        print()
        raise OpsError("向导未收到输入（EOF / %ds 超时），已退出" % timeout,
                       EXIT_USAGE) from None
    except KeyboardInterrupt:
        print()
        print("(取消)")
        raise OpsError("向导被取消（Ctrl+C）", EXIT_USAGE) from None
    finally:
        if has_alarm:
            signal.alarm(0)
            signal.signal(signal.SIGALRM, old)
    return answer or default


def _non_tty() -> int:
    """管道 / -o json / 非交互终端：不提问，直接给可复制的命令模板。"""
    ui.out("===== 一键发布向导（非交互模式）=====")
    ui.out("默认不提问（避免管道 / 自动化被 input 挂住）。可复制下列命令；"
           "进入问答请加 -i 并在真实终端运行：")
    ui.out("")
    for line in _TEMPLATES:
        ui.out("  " + line)
    ui.out("")
    ui.out("去掉 --yes 即只读预演；红岛生产另需 --env hongdao --allow-prod。")
    ui.out("交互式问答：./itp-ops.sh flow guide -i（只生成命令，NEVER 执行写操作）。")
    ui.result({"interactive": False, "commands": list(_TEMPLATES)})
    return 0


def _questions(name: str) -> tuple[list[str], dict]:
    """返回生成的命令片段与 definitions.build 需要的参数。"""
    parts: list[str] = ["flow", name]
    opts: dict = {}

    if name == "backend":
        module = _ask("模块目录名（如 face-pay-server）", "face-pay-server")
        parts.append(module)
        opts["module"] = module
        app = _ask("CloudOS 应用名（回车=同模块名）", module)
        image = _ask("Harbor 镜像名（回车=同模块名）", module)
        version = _ask("版本（回车=自动 bump）")
        if app != module:
            parts += ["--app", app]
        if image != module:
            parts += ["--image", image]
        if version:
            parts += ["--version", version]
        opts.update(app=app, image=image)
        if version:
            opts["version"] = version
    elif name == "frontend":
        mode = _ask("版本模式 1=--bump 2=--debug 3=指定版本", "1")
        if mode == "2":
            parts.append("--debug")
            opts["debug"] = True
        elif mode == "3":
            v = _ask("版本号")
            parts.append(v)
            opts["version"] = v
        else:
            parts.append("--bump")
    else:
        app = _ask("应用名（如 face-pay-server）", "face-pay-server")
        parts.append(app)
        opts["app"] = app
        size = _ask("容器规格 4Gi / 8Gi", "4Gi")
        parts += ["--size", size]
        opts["size"] = size

    env = _ask("环境 test / hongdao", "test")
    parts += ["--env", env]
    opts["env"] = env
    return parts, opts


def run(interactive: bool = False) -> int:
    ctx = ui.context()
    if ctx.json_mode or not interactive or not sys.stdin.isatty():
        return _non_tty()

    ui.out("===== 一键发布向导 =====")
    ui.out("选择工作流：")
    for i, (name, desc) in enumerate(_CHOICES, 1):
        ui.out("  %d. %-9s %s" % (i, name, desc))
    pick = _ask("输入 1/2/3", "1")
    if pick not in ("1", "2", "3"):
        raise OpsError("无效选择：%s（只能输入 1/2/3）" % pick, EXIT_USAGE)
    name = _CHOICES[int(pick) - 1][0]

    parts, opts = _questions(name)
    cmd = "./itp-ops.sh " + " ".join(parts)

    ui.out("")
    ui.out("===== 已生成命令（只读预演，不含 --yes）=====")
    ui.out("  " + cmd)
    ui.out("")
    ui.out("这条命令会按以下顺序执行：")
    flow = definitions.build(name, opts)
    for i, step in enumerate(flow.steps, 1):
        ui.out("  %2d. [%s] %s" % (i, "写" if step.write else "读", step.title))
    for note in flow.notes:
        ui.out("  ⚠ " + note)
    ui.out("")
    ui.out("确认执行：在上面命令末尾加 --yes；红岛生产另加 --env hongdao --allow-prod。")
    ui.result({"interactive": True, "workflow": name, "command": cmd,
               "steps": [s.title for s in flow.steps]})
    return 0
