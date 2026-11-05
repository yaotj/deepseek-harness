"""扩展工作流定义：联调 / 回滚预案 / 批量升级 / 环境清理 / 健康日报。

这些工作流是对核心工作流（backend/frontend/jvm/health/rollback）的补充，
提供更高级的运维场景支持。
"""
from __future__ import annotations

from ..errors import EXIT_PRECONDITION, EXIT_USAGE, OpsError
from .runner import Flow, Step


def integration(o: dict) -> Flow:
    """联调工作流：多服务一键部署 + 联调验证。

    用途：
      联调新接口时，需要同时部署多个关联服务并验证交互。

    示例：
      flow integration face-pay-server gate-txn-pay-server --env test --yes
    """
    modules = o.get("modules", [])
    if not modules:
        raise OpsError("用法：flow integration <模块1> <模块2> [...] [--env test] [--yes]", EXIT_USAGE)

    env = o.get("env") or "test"

    # 构建步骤：对每个模块执行 backend 流程
    steps: list[Step] = []
    for i, module in enumerate(modules, 1):
        steps.append(Step(
            f"[{i}/{len(modules)}] 部署 {module}",
            ("flow", "backend", module, "--env", env) + (("--yes",) if o.get("yes") else ()),
            write=True,
        ))

    # 联调验证步骤
    steps.append(Step("联调验证：调用健康检查接口",
                      ("--env", env, "cloudos", "apps"),
                      expect="应用"))

    return Flow(
        "integration",
        f"联调工作流：批量部署 {len(modules)} 个服务并验证",
        tuple(steps),
        notes=(
            f"将部署以下模块：{', '.join(modules)}",
            "联调验证会检查所有应用状态，确认无 Error。",
            "这是写操作，MUST 加 --yes；生产环境另需 --allow-prod。",
        ),
    )


def rollback_plan(o: dict) -> Flow:
    """回滚预案工作流：查询当前版本 + 生成回滚方案。

    用途：
      发布前查看当前版本信息，生成回滚命令预案。

    示例：
      flow rollback-plan face-pay-server --env test
    """
    app = o.get("app") or o.get("module")
    if not app:
        raise OpsError("用法：flow rollback-plan <应用> [--env test]", EXIT_USAGE)

    env = o.get("env") or "test"

    return Flow(
        "rollback-plan",
        f"回滚预案：查询 {app} 当前版本并生成回滚方案",
        (
            Step("查询当前镜像 tag",
                 ("--env", env, "cloudos", "app", app, "--output", "json"),
                 json_path="data.imageTag", var="current_tag"),
            Step("显示回滚预案",
                 ("echo", f"回滚命令：./itp-ops.sh flow rollback {app} --to <version> --env {env} --yes")),
        ),
        notes=(
            f"当前应用：{app}",
            "回滚预案仅供参考，实际回滚 MUST 使用 flow rollback 命令。",
            "发布前建议记录当前版本，便于紧急回滚。",
        ),
    )


def batch_jvm(o: dict) -> Flow:
    """批量 JVM 升级工作流。

    用途：
      一次性升级多个服务的 JVM 参数（如统一调整堆内存）。

    示例：
      flow batch-jvm --apps face-pay-server gate-txn-pay-server --size 4Gi --env test --yes
    """
    apps = o.get("apps", [])
    if not apps:
        raise OpsError("用法：flow batch-jvm --apps <应用1> <应用2> [...] [--size 4Gi|8Gi] [--env test] [--yes]", EXIT_USAGE)

    env = o.get("env") or "test"
    size = o.get("size") or "4Gi"

    steps: list[Step] = []
    for i, app in enumerate(apps, 1):
        steps.append(Step(
            f"[{i}/{len(apps)}] 升级 {app} JVM 参数",
            ("flow", "jvm", app, "--size", size, "--env", env) + (("--yes",) if o.get("yes") else ()),
            write=True,
        ))

    return Flow(
        "batch-jvm",
        f"批量 JVM 升级：{len(apps)} 个服务（{size} 容器）",
        tuple(steps),
        notes=(
            f"将升级以下应用：{', '.join(apps)}",
            f"容器规格：{size}，目标堆内存：{'2900m' if size == '4Gi' else '5900m'}",
            "批量操作是写操作，MUST 加 --yes；生产环境另需 --allow-prod。",
        ),
    )


def batch_bump(o: dict) -> Flow:
    """批量版本 bump 工作流。

    用途：
      一次性 bump 多个模块的版本号。

    示例：
      flow batch-bump --dir account-server daily-ticket-server --env test
    """
    dirs = o.get("dirs", [])
    if not dirs:
        raise OpsError("用法：flow batch-bump --dir <模块1> <模块2> [...] [--env test]", EXIT_USAGE)

    env = o.get("env") or "test"

    steps: list[Step] = []
    for i, module in enumerate(dirs, 1):
        steps.append(Step(
            f"[{i}/{len(dirs)}] bump {module} 版本",
            ("version", "bump", "--dir", module),
            write=True,
        ))

    return Flow(
        "batch-bump",
        f"批量版本 bump：{len(dirs)} 个模块",
        tuple(steps),
        notes=(
            f"将 bump 以下模块：{', '.join(dirs)}",
            "批量操作会修改 pom.xml，建议先 git diff 确认。",
        ),
    )


def cleanup(o: dict) -> Flow:
    """环境清理工作流。

    用途：
      清理测试环境中的停止应用、残留资源。

    示例：
      flow cleanup --env test --yes
      flow cleanup --ns itp --yes
    """
    env = o.get("env") or "test"
    namespace = o.get("ns") or "itp"

    return Flow(
        "cleanup",
        f"环境清理：{env} 环境（ns={namespace}）",
        (
            Step("查找停止状态的应用",
                 ("--env", env, "cloudos", "apps", "Stopped"),
                 expect="应用"),
            Step("清理停止的应用（需确认）",
                 ("echo", "请手动执行清理操作，或联系运维负责人"),
                 write=False),
        ),
        notes=(
            "清理操作会影响测试环境，MUST 确认后再执行。",
            "生产环境禁止使用此工作流。",
            "建议先运行预演模式查看将清理哪些应用。",
        ),
    )


def health_report(o: dict) -> Flow:
    """健康检查日报工作流。

    用途：
      生成环境健康检查报告（HTML/文本格式）。

    示例：
      flow health-report --env test --output report.html
      flow health-report --env test
    """
    env = o.get("env") or "test"
    output = o.get("output") or ""

    steps: list[Step] = [
        Step("环境健康检查",
             ("flow", "health", "--env", env)),
        Step("应用状态统计",
             ("--env", env, "cloudos", "apps"),
             expect="应用"),
        Step("异常应用列表",
             ("--env", env, "cloudos", "apps", "Error"),
             expect="应用"),
    ]

    if output:
        steps.append(Step("生成报告",
                          ("echo", f"报告已生成：{output}"),
                          write=True))

    return Flow(
        "health-report",
        f"健康检查日报：{env} 环境",
        tuple(steps),
        notes=(
            "报告包含：环境健康状态 + 应用列表 + 异常告警",
            f"输出格式：{'HTML 文件' if output.endswith('.html') else '文本'}",
            "建议定时运行（如每日早会前），生成历史趋势对比。",
        ),
    )


def summarize() -> list[tuple[str, str, str]]:
    """给 `flow list` 用：(名称, 用途, 用法)。"""
    return [
        ("integration", "联调工作流（批量部署 + 联调验证）",
         "flow integration <模块1> <模块2> [...] [--env test] [--yes]"),
        ("rollback-plan", "回滚预案（查询当前版本 + 生成回滚方案）",
         "flow rollback-plan <应用> [--env test|hongdao]"),
        ("batch-jvm", "批量 JVM 升级（一次性升级多个服务）",
         "flow batch-jvm --apps <应用1> <应用2> [...] [--size 4Gi|8Gi] [--env test] [--yes]"),
        ("batch-bump", "批量版本 bump（一次性 bump 多个模块）",
         "flow batch-bump --dir <模块1> <模块2> [...] [--env test]"),
        ("cleanup", "环境清理（清理停止应用 + 残留资源）",
         "flow cleanup --env test [--ns itp] [--yes]"),
        ("health-report", "健康检查日报（生成环境健康报告）",
         "flow health-report [--env test] [--output report.html]"),
    ]


def build(name: str, o: dict) -> Flow:
    """根据名称构建工作流。"""
    if name == "integration":
        return integration(o)
    if name == "rollback-plan":
        return rollback_plan(o)
    if name == "batch-jvm":
        return batch_jvm(o)
    if name == "batch-bump":
        return batch_bump(o)
    if name == "cleanup":
        return cleanup(o)
    if name == "health-report":
        return health_report(o)

    # 委托给核心工作流
    from .definitions import build as core_build
    return core_build(name, o)
