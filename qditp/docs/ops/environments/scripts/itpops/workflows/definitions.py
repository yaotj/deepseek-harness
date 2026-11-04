"""三条一键工作流定义：后端发布 / 前端发布 / JVM 参数升级。

这里只声明步骤，不执行任何动作；执行由 runner.run() 负责。
每个 itpops 步骤都是一条现有命令，MUST 保持「唯一入口」——NEVER 在这里直接调
buildx.core / web.frontend_release / cloudos.deploy 的内部函数。
"""
from __future__ import annotations

from ..core.runtime import repo_root, scripts_dir
from ..errors import EXIT_PRECONDITION, EXIT_USAGE, OpsError
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
    return Flow(
        "backend",
        "后端一键发布：自检 → bump → 出 jar → 出镜像 → deploy 预演 → 切镜像（%s）" % module,
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


def summarize() -> list[tuple[str, str, str]]:
    """给 `flow list` 用：(名称, 用途, 用法)。"""
    return [
        ("backend", "后端一键发布（自检→bump→jar→镜像→切镜像）",
         "flow backend <模块目录> [--app <应用>] [--image <镜像>] [--version <v>] [--yes]"),
        ("frontend", "前端一键发布（自检→web release --bump/--debug）",
         "flow frontend [--bump|--debug|--version <v>] [--skip-build] [--yes]"),
        ("jvm", "JVM 三件套一键升级（IT-020，默认 4Gi → 堆 2900m）",
         "flow jvm <应用> [--size 4Gi|8Gi] [--yes]"),
    ]


def build(name: str, o: dict) -> Flow:
    if name == "backend":
        return backend(o)
    if name == "frontend":
        return frontend(o)
    if name == "jvm":
        return jvm(o)
    raise OpsError("未知工作流：%s（可用 %s）" % (name, " / ".join(x[0] for x in summarize())),
                   EXIT_PRECONDITION)
