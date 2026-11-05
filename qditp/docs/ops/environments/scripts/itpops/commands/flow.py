"""flow 域 —— 工作流一键入口（后端发布 / 前端发布 / JVM 参数升级 / 扩展工作流）。

设计目的：把「发一次版要连敲 7 条命令」变成一条，同时不牺牲安全性：
  · 不加 --yes 只列步骤（离线也能预演），加 --yes 才逐步执行；
  · 每步都走统一入口 python -m itpops，不绕过全局参数 / JSON 信封 / 退出码；
  · 每步有判据（expect），失败即断并留下已完成步骤与变量；
  · 红岛生产 MUST 额外 --allow-prod；flow 默认环境 test（NEVER 默认打生产）。

扩展工作流（2026-11-05 新增）：
  · integration：联调工作流（批量部署 + 联调验证）
  · rollback-plan：回滚预案（查询当前版本 + 生成回滚方案）
  · batch-jvm：批量 JVM 升级
  · batch-bump：批量版本 bump
  · cleanup：环境清理
  · health-report：健康检查日报
"""
from __future__ import annotations

from ..cli.suggest import append_hint
from ..core import ui
from ..errors import EXIT_USAGE, OpsError
from ..workflows import definitions, extended, runner, wizard

CATALOG = {
    "name": "flow",
    "summary": "工作流一键入口：后端发布 / 前端发布 / JVM 参数升级（声明式步骤 + 预演 + 失败即断）",
    "default_env": "test",
    "env_note": "flow 默认 test；--env hongdao 生产必须额外 --allow-prod",
    "subcommands": [
        {"name": "guide", "usage": "flow guide [-i|--interactive]",
         "summary": "发布向导：默认输出可复制的命令模板；-i 进入交互问答（真实终端）。两者都只生成命令，NEVER 执行写操作",
         "args": [{"name": "-i / --interactive", "required": False}],
         "writes": False, "example": "itp-ops.sh flow guide -i",
         "stdout_human": "===== 一键发布向导 ===== … 已生成命令 …",
         "json_data": {"interactive": True, "command": "./itp-ops.sh flow backend …"}},
        {"name": "list", "usage": "flow list", "summary": "列出可用工作流、依赖图与用法",
         "args": [], "writes": False, "example": "itp-ops.sh flow list",
         "stdout_human": "===== 可用工作流（flow）===== … ===== 依赖关系图 ===== …",
         "json_data": {"flows": [{"name": "health"}, {"name": "backend"}]}},
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
        {"name": "rollback",
         "usage": "flow rollback <应用> --to <version> 或 --last [--env test|hongdao] [--yes]",
         "summary": "应用回滚：查询当前镜像 tag → 切换到目标版本 → 验证收敛",
         "args": [{"name": "应用", "required": True},
                  {"name": "--to", "required": False},
                  {"name": "--last", "required": False}],
         "writes": True,
         "example": "itp-ops.sh flow rollback face-pay-server --to 1.0.89 --env test --yes",
         "stdout_human": "===== 工作流 rollback（test）===== … ✓ 工作流 rollback 完成（2 步）",
         "json_data": {"flow": "rollback", "done": True}},
    ],
}

_VALUE_FLAGS = ("--module", "--app", "--image", "--version", "--size", "--jar", "--dockerfile", "--to", "--apps", "--dirs", "--ns", "--output")
_BOOL_FLAGS = ("--bump", "--debug", "--skip-build", "--allow-prod", "-i", "--interactive")


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
            opts[a.lstrip("-").replace("-", "_")] = True
            i += 1
        elif a.startswith("-"):
            raise OpsError(
                append_hint(f"未知参数：{a}（flow -h 查看用法）", a,
                            _VALUE_FLAGS + _BOOL_FLAGS + ("--env", "--yes", "--dry-run")),
                EXIT_USAGE)
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
        ui.out("  不知道怎么拼命令？./itp-ops.sh flow guide 出命令模板，加 -i 进交互问答（都不执行）。")
        ui.out("  backend 的位置参数 = 仓库根下的模块目录名（如 face-pay-server）；"
               "CloudOS 应用名用 --app、Harbor 镜像名用 --image 单独指定，也可用 --module 代替位置参数。")
        ui.out("")
        ui.out(definitions.dependency_diagram())
        ui.result({"flows": [{"name": n, "summary": s, "usage": u} for n, s, u in rows],
                   "default_env": "test",
                   "diagram": definitions.dependency_diagram()})
        return 0

    if sub == "guide":
        _, guide_opts = _parse(args[1:])
        return wizard.run(interactive=bool(guide_opts.get("interactive") or guide_opts.get("i")))

    positional, opts = _parse(args[1:])
    opts["env"] = env
    if sub == "health":
        return runner.run(definitions.build("health", opts), opts)
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
    elif sub == "rollback":
        if len(positional) > 1:
            raise OpsError(f"多余位置参数：{positional[1:]}", EXIT_USAGE)
        opts["app"] = positional[0] if positional else opts.get("app")
        opts["to"] = opts.get("to")
        opts["last"] = opts.get("last")
        if not opts["app"]:
            raise OpsError("用法：flow rollback <应用> --to <version> 或 --last", EXIT_USAGE)
    elif sub == "integration":
        # 位置参数为模块列表
        opts["modules"] = positional if positional else opts.get("modules", [])
        if not opts["modules"]:
            raise OpsError("用法：flow integration <模块1> <模块2> [...] 或 --modules <模块1> <模块2>", EXIT_USAGE)
    elif sub == "rollback-plan":
        opts["app"] = positional[0] if positional else opts.get("app")
        if not opts["app"]:
            raise OpsError("用法：flow rollback-plan <应用> 或 --app <应用>", EXIT_USAGE)
    elif sub == "batch-jvm":
        apps = positional if positional else opts.get("apps", [])
        if not apps:
            raise OpsError("用法：flow batch-jvm --apps <应用1> <应用2> [...] 或位置参数", EXIT_USAGE)
        opts["apps"] = apps
    elif sub == "batch-bump":
        dirs = positional if positional else opts.get("dirs", [])
        if not dirs:
            raise OpsError("用法：flow batch-bump --dir <模块1> <模块2> [...] 或位置参数", EXIT_USAGE)
        opts["dirs"] = dirs
    elif sub == "cleanup":
        opts["ns"] = opts.get("ns", "itp")
    elif sub == "health-report":
        opts["output"] = opts.get("output", "")
    elif sub != "frontend":
        raise OpsError(
            append_hint("未知工作流：%s（可用 flow list）" % sub, sub,
                        [x[0] for x in definitions.summarize()] +
                        [x[0] for x in extended.summarize()] + ["guide"]),
            EXIT_USAGE)

    # 优先尝试扩展工作流，找不到再委托核心工作流
    try:
        flow = extended.build(sub, opts)
    except Exception:
        flow = definitions.build(sub, opts)
    return runner.run(flow, opts)
