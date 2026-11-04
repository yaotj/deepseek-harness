"""flow 域 —— 工作流一键入口（后端发布 / 前端发布 / JVM 参数升级）。

设计目的：把「发一次版要连敲 7 条命令」变成一条，同时不牺牲安全性：
  · 不加 --yes 只列步骤（离线也能预演），加 --yes 才逐步执行；
  · 每步都走统一入口 python -m itpops，不绕过全局参数 / JSON 信封 / 退出码；
  · 每步有判据（expect），失败即断并留下已完成步骤与变量；
  · 红岛生产 MUST 额外 --allow-prod；flow 默认环境 test（NEVER 默认打生产）。
"""
from __future__ import annotations

from ..core import ui
from ..errors import EXIT_USAGE, OpsError
from ..workflows import definitions, runner

CATALOG = {
    "name": "flow",
    "summary": "工作流一键入口：后端发布 / 前端发布 / JVM 参数升级（声明式步骤 + 预演 + 失败即断）",
    "default_env": "test",
    "env_note": "flow 默认 test；--env hongdao 生产必须额外 --allow-prod",
    "subcommands": [
        {"name": "list", "usage": "flow list", "summary": "列出可用工作流与用法",
         "args": [], "writes": False, "example": "itp-ops.sh flow list",
         "stdout_human": "===== 可用工作流（flow）===== …",
         "json_data": {"flows": [{"name": "backend"}]}},
        {"name": "backend",
         "usage": "flow backend <模块目录|--module <目录>> [--app <CloudOS应用>] [--image <镜像名>] [--version <v>] [--jar <p>] [--dockerfile <p>] [--yes]",
         "summary": "后端一键发布：自检 → bump → 出 fat jar → tag 占用检查 → 平台 x86 出镜像 → deploy 预演 → 切镜像收敛",
         "args": [{"name": "模块目录 / --module", "required": True},
                  {"name": "--app", "required": False}, {"name": "--image", "required": False},
                  {"name": "--version", "required": False}, {"name": "--jar", "required": False},
                  {"name": "--dockerfile", "required": False},
                  {"name": "--allow-prod", "required": False}],
         "writes": True,
         "example": "itp-ops.sh flow backend face-pay-server --env test --yes",
         "stdout_human": "===== 工作流 backend（test）===== … [1/7] 云管台连通性自检 … ✓ 工作流 backend 完成（7 步）",
         "json_data": {"flow": "backend", "done": True}},
        {"name": "frontend",
         "usage": "flow frontend [--bump|--debug|--version <v>] [--skip-build] [--yes]",
         "summary": "前端一键发布：自检 → web release（出 zip → 上传软件中心 → 升级 → 核验）。默认 --bump 自动算下一版",
         "args": [{"name": "--bump", "required": False}, {"name": "--debug", "required": False},
                  {"name": "--version", "required": False},
                  {"name": "--skip-build", "required": False}],
         "writes": True,
         "example": "itp-ops.sh flow frontend --env test --yes",
         "stdout_human": "===== 工作流 frontend（test）===== … ✓ 工作流 frontend 完成（2 步）",
         "json_data": {"flow": "frontend", "done": True}},
        {"name": "jvm",
         "usage": "flow jvm <应用> [--size 4Gi|8Gi] [--yes]",
         "summary": "JVM 三件套一键升级（IT-020）：默认 4Gi → 4000/1000/100 堆 2900m；8Gi → 8000/2000/100 堆 5900m",
         "args": [{"name": "应用", "required": True}, {"name": "--size", "required": False}],
         "writes": True,
         "example": "itp-ops.sh flow jvm face-pay-server --env test --size 4Gi --yes",
         "stdout_human": "===== 工作流 jvm（test）===== … ✓ 工作流 jvm 完成（3 步）",
         "json_data": {"flow": "jvm", "done": True}},
    ],
}

_VALUE_FLAGS = ("--module", "--app", "--image", "--version", "--size", "--jar", "--dockerfile")
_BOOL_FLAGS = ("--bump", "--debug", "--skip-build", "--allow-prod")


def _parse(args: list[str]) -> tuple[list[str], dict]:
    positional: list[str] = []
    opts: dict = {}
    i = 0
    while i < len(args):
        a = args[i]
        if a in _VALUE_FLAGS:
            if i + 1 >= len(args) or args[i + 1].startswith("--"):
                raise OpsError(f"{a} 缺值", EXIT_USAGE)
            opts[a[2:]] = args[i + 1]
            i += 2
        elif a in _BOOL_FLAGS:
            opts[a[2:].replace("-", "_")] = True
            i += 1
        elif a.startswith("-"):
            raise OpsError(f"未知参数：{a}（flow -h 查看用法）", EXIT_USAGE)
        else:
            positional.append(a)
            i += 1
    return positional, opts


def main(args: list[str]) -> int:
    ctx = ui.context()
    env = ctx.env or "test"
    sub = args[0] if args else "list"

    if sub in ("list", "help", "-h", "--help"):
        ui.out("===== 可用工作流（flow）=====")
        rows = definitions.summarize()
        for name, summary, usage in rows:
            ui.out("  %-10s %s" % (name, summary))
            ui.out("             %s" % usage)
        ui.out("")
        ui.out("  backend 的位置参数 = 仓库根下的模块目录名（如 face-pay-server）；"
               "CloudOS 应用名用 --app、Harbor 镜像名用 --image 单独指定，也可用 --module 代替位置参数。")
        ui.out("")
        ui.out("不加 --yes 只列步骤（离线可预演）；确认执行加 --yes；"
               "红岛生产另需 --allow-prod。默认环境 test。")
        ui.result({"flows": [{"name": n, "summary": s, "usage": u} for n, s, u in rows],
                   "default_env": "test"})
        return 0

    positional, opts = _parse(args[1:])
    opts["env"] = env
    if sub == "backend":
        if len(positional) > 1:
            raise OpsError(f"多余位置参数：{positional[1:]}", EXIT_USAGE)
        opts["module"] = positional[0] if positional else opts.get("module")
        if not opts["module"]:
            raise OpsError("用法：flow backend <模块目录> 或 --module <目录>（如 face-pay-server）；"
                           "CloudOS 应用名另用 --app 指定", EXIT_USAGE)
    elif sub == "jvm":
        if len(positional) > 1:
            raise OpsError(f"多余位置参数：{positional[1:]}", EXIT_USAGE)
        opts["app"] = positional[0] if positional else opts.get("app")
        if not opts["app"]:
            raise OpsError("用法：flow jvm <应用> 或 --app <应用> [--size 4Gi|8Gi] [--yes]", EXIT_USAGE)
    elif sub != "frontend":
        raise OpsError("未知工作流：%s（可用 flow list）" % sub, EXIT_USAGE)

    return runner.run(definitions.build(sub, opts), opts)
