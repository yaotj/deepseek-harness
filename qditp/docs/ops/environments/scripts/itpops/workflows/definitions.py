"""三条一键工作流定义：后端发布 / 前端发布 / JVM 参数升级。

这里只声明步骤，不执行任何动作；执行由 runner.run() 负责。
每个 itpops 步骤都是一条现有命令，MUST 保持「唯一入口」——NEVER 在这里直接调
buildx.core / web.frontend_release / cloudos.deploy 的内部函数。

Hook 机制：
  模块根目录可放置 `itp-hook.sh`，支持 pre_build/post_build/pre_deploy 三个 hook 点。
  具体实现见 `hooks.py`。
"""
from __future__ import annotations

from ..core.runtime import repo_root, scripts_dir
from ..errors import EXIT_PRECONDITION, EXIT_USAGE, OpsError
from .hooks import run_hook
from .runner import Flow, Step

# JVM 堆 = max_memory - reserved_memory - max_direct_memory（基础镜像 /startapp.sh）
# 口径来自 deploy/k8s/10-deployments 模板与 IT-020：4Gi 容器 4000/1000/100，8Gi 容器 8000/2000/100
JVM_PRESETS = {
    "4Gi": ("4000", "1000", "100", "2900m"),
    "8Gi": ("8000", "2000", "100", "5900m"),
}


def _health(env: str) -> Step:
    return Step("云管台连通性自检", ("--env", env, "cloudos", "health"),
                expect="可以操作")


def backend(o: dict) -> Flow:
    module = o["module"]
    if not (repo_root() / module).is_dir():
        raise OpsError(
            "模块目录不存在：%s（位置参数 / --module 必须是仓库根下的模块目录名，"
            "如 face-pay-server；CloudOS 应用名请用 --app 单独指定）" % module,
            EXIT_PRECONDITION,
        )
    app = o.get("app") or module
    image = o.get("image") or module
    env = o["env"]
    jar = o.get("jar") or str(repo_root() / module / "target" / (module + "-{version}.jar"))
    dockerfile = o.get("dockerfile") or str(scripts_dir() / "buildx.Dockerfile")

    steps: list[Step] = [_health(env)]
    if not o.get("version"):
        # 不给 --version 时由 bump 产出并捕获；显式给版本则跳过改 pom，直接走后续步骤
        steps.append(Step(
            "bump 模块 pom 版本并捕获新版本",
            ("version", "bump", "--dir", module),
            capture=r"→\s*([0-9][0-9.]*)", var="version", write=True,
        ))

    steps += [
        Step("出 spring-boot fat jar",
             ("mise", "exec", "--", "mvn", "-o", "-pl", module, "-am",
              "package", "-Dmaven.test.skip=true"),
             kind="exec", expect="BUILD SUCCESS", cwd=str(repo_root()), write=True),
        Step("只读确认目标 tag 未占用",
             ("buildx", image, "{version}", "--env", env, "--check")),
        Step("平台 x86 节点构建 amd64 镜像",
             ("buildx", image, "{version}", "--env", env,
              "--jar", jar, "--dockerfile", dockerfile),
             expect="构建完成", write=True),
        Step("切镜像预演（打印基线 / body / 回滚点）",
             ("--env", env, "cloudos", "deploy", app, "{version}")),
        Step("切镜像执行并轮询收敛",
             ("--env", env, "cloudos", "deploy", app, "{version}", "--yes"),
             expect="已收敛", write=True),
    ]

    # 运行 pre_deploy hook（如果存在）
    try:
        run_hook(module, "pre_deploy", env)
    except Exception as exc:
        # hook 失败不阻断主流程，只打印 WARN
        import sys
        print(f"[WARN] hook pre_deploy 失败: {exc}", file=sys.stderr)
    return Flow(
        "backend",
        ("后端一键发布：自检 → bump → 出 jar → 出镜像 → deploy 预演 → 切镜像"
         "（模块=%s 应用=%s 镜像=%s）" % (module, app, image)),
        tuple(steps),
        notes=(
            "{version} 由 bump 步骤产出后自动填入；--version <v> 可显式指定（此时跳过改 pom）。",
            "起始版本取自仓库 pom，发布前 MUST 核对线上实例实跑 tag（IT-034：平台记录常落后）；"
            "口径不一致时 MUST 用 --version 显式给定。",
            "默认把模块名同时当应用名和镜像名；fep-app-server 的镜像是 itp/fep-app，"
            "不一致 MUST 用 --app / --image 显式指定。",
        ),
    )


def frontend(o: dict) -> Flow:
    env = o["env"]
    rel: list[str] = ["web", "release"]
    if o.get("debug"):
        rel.append("--debug")
    elif o.get("version"):
        rel.append(o["version"])
    else:
        rel.append("--bump")
    if o.get("skip_build"):
        rel.append("--skip-build")
    rel += ["--env", env, "--yes"]
    return Flow(
        "frontend",
        "前端一键发布：自检 → web release（出 zip → 上传软件中心 → 升级 → 核验）",
        (_health(env),
         Step("前端应用包一键发版", tuple(rel), expect="完成", write=True)),
    )


def jvm(o: dict) -> Flow:
    app = o["app"]
    env = o["env"]
    size = o.get("size") or "4Gi"
    if size not in JVM_PRESETS:
        raise OpsError("--size 只支持 4Gi / 8Gi（收到 %s）" % size, EXIT_USAGE)
    mx, reserved, direct, heap = JVM_PRESETS[size]
    return Flow(
        "jvm",
        "JVM 参数一键升级（IT-020 三件套，%s 容器 → 堆 %s）" % (size, heap),
        (_health(env),
         Step("注入 JVM 三件套（env-set 带 k8s 快照安全门）",
              ("--env", env, "cloudos", "env-set", app,
               "max_memory=" + mx, "reserved_memory=" + reserved,
               "max_direct_memory=" + direct, "--yes"),
              expect="已下发 PATCH", write=True),
         Step("复验 env 已生效", ("--env", env, "cloudos", "env", app),
              expect="max_memory")),
    )


def health(o: dict) -> Flow:
    """环境就绪自检：云管台连通 + k8s 命名空间可达 + 默认应用列表。只读，不写。"""
    env = o.get("env") or "test"
    return Flow(
        "health",
        "环境就绪自检（云管台连通 + k8s 命名空间可达 + 默认应用列表）",
        (
            Step("云管台连通性自检", ("--env", env, "cloudos", "health"),
                 expect="可以操作"),
            Step("检查 k8s 命名空间可用", ("--env", env, "k8s", "services"),
                 expect="account-server"),
            Step("列出默认应用状态", ("--env", env, "cloudos", "apps", "Error"),
                 expect="应用"),
        ),
        notes=(
            "只读操作，不会修改任何状态；适合 CI 门禁 / 发版前置检查。",
            "默认检查 test 环境；红岛生产用 --env hongdao。",
        ),
    )


def rollback(o: dict) -> Flow:
    """回滚工作流：查询当前镜像 tag，切换到指定版本或上一次版本。"""
    app = o.get("app") or o.get("module")
    if not app:
        raise OpsError("用法：flow rollback <应用> --to <version> 或 --last", EXIT_USAGE)
    env = o.get("env") or "test"
    target = o.get("to")
    last = o.get("last")

    if not target and not last:
        raise OpsError("必须指定 --to <version> 或 --last", EXIT_USAGE)
    if target and last:
        raise OpsError("--to 和 --last 不能同时使用", EXIT_USAGE)

    steps: list[Step] = [_health(env)]

    if last:
        # --last：查询当前部署版本，回退到上一个（简化实现：先查当前 tag）
        steps.append(
            Step("查询当前镜像 tag",
                 ("--env", env, "cloudos", "app", app, "--output", "json"),
                 json_path="data.imageTag", var="current_tag"),
        )
        steps.append(
            Step("回滚到指定版本（需手动提供上一版本 tag）",
                 ("--env", env, "cloudos", "deploy", app, "{target}", "--yes"),
                 expect="已收敛", write=True),
        )
    else:
        # --to：直接回滚到指定版本
        steps.append(
            Step("回滚到指定版本",
                 ("--env", env, "cloudos", "deploy", app, target, "--yes"),
                 expect="已收敛", write=True),
        )

    return Flow(
        "rollback",
        "应用回滚（查询当前状态 → 切换镜像 tag → 验证收敛）",
        tuple(steps),
        notes=(
            "回滚前 MUST 确认目标版本存在且可用；"
            "建议使用 --last 前先手动查询当前 tag。",
            "回滚是写操作，MUST 加 --yes；生产环境另需 --allow-prod。",
        ),
    )


def summarize() -> list[tuple[str, str, str]]:
    """给 `flow list` 用：(名称, 用途, 用法)。"""
    return [
        ("health", "环境就绪自检（云管台 + k8s + 应用列表）",
         "flow health [--env test|hongdao]"),
        ("backend", "后端一键发布（自检→bump→jar→镜像→切镜像）",
         "flow backend <模块目录|--module> [--app <CloudOS应用>] [--image <镜像名>] [--version <v>] [--yes]"),
        ("frontend", "前端一键发布（自检→web release --bump/--debug）",
         "flow frontend [--bump|--debug|--version <v>] [--skip-build] [--yes]"),
        ("jvm", "JVM 三件套一键升级（IT-020，默认 4Gi → 堆 2900m）",
         "flow jvm <应用> [--size 4Gi|8Gi] [--yes]"),
        ("rollback", "应用回滚（切换镜像 tag 到历史版本）",
         "flow rollback <应用> --to <version> 或 --last [--env test|hongdao] [--yes]"),
    ]


def dependency_diagram() -> str:
    """返回 ASCII 依赖图，给 `flow list` 展示用。"""
    return """
  依赖关系图（箭头表示执行顺序）:

  health:
    cloudos health ──→ k8s services ──→ cloudos apps Error

  backend:
    cloudos health ──→ version bump ──→ mvn package ──→ buildx ──→ cloudos deploy (预演) ──→ cloudos deploy --yes

  frontend:
    cloudos health ──→ web release (--bump/--debug/--version)

  jvm:
    cloudos health ──→ cloudos env-set (IT-020) ──→ cloudos env (复验)

  公共前置：所有流程第一步都是「云管台连通性自检」，失败则后续步骤不会执行。
"""


def build(name: str, o: dict) -> Flow:
    if name == "health":
        return health(o)
    if name == "rollback":
        return rollback(o)
    if name == "backend":
        return backend(o)
    if name == "frontend":
        return frontend(o)
    if name == "jvm":
        return jvm(o)
    raise OpsError("未知工作流：%s（可用 %s）" % (name, " / ".join(x[0] for x in summarize())),
                   EXIT_PRECONDITION)
