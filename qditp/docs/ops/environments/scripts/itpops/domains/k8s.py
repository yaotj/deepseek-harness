"""itpops · k8s 域 —— 镜像构建推送（本机 jar → 跳板 → Harbor）。

移植自 domains/k8s.sh（bash，297 行）：deploy / deploy-all / services。
命令面与旧 bash 逐字兼容：子命令名、参数形态、stdout 结果文本一致；help 文案
原样拷贝（`k8s` / `k8s help` / `k8s -h` 与 legacy stdout diff 为空）。

【环境限制（MUST，保留自 bash 头注）】
  本域只对测试环境可用：红岛跳板 hd-linux 既无 /home/java 构建目录也无
  build-push.sh（2026-10-26 实测，远端只有 podman 没有 docker）——红岛发版走
  `itp-ops.sh cloudos image build`（平台 x86 节点打镜像），NEVER 用本域打红岛。

【ALL_SERVICES 纪律（MUST，逐条保留自 bash）】
  · 服务名 MUST 是仓库根目录下的模块目录名（不是镜像名、不是 Deployment 名）。
  · 语义 = 可构建推送的模块全集（根 pom 中有 kubernetes-maven-plugin 的模块目录），
    不是生产部署清单——NEVER 按生产清单裁剪（会改变 deploy/deploy-all 的全量语义）：
    本清单有、生产不需要：acc-secure-server（死模块）、collect-pay-server（已下线）、
    collect-ticket-server（未部署）；生产还有 web-server/web 前端——走 zip + nginx
    Deployment，NEVER 加进本清单。
  · 镜像名 ≠ 模块名：fep-app-server 的镜像名是 itp/fep-app（<image.prefix>），
    核对推送结果 MUST 先 grep pom 的 <image.prefix>，NEVER 用目录名拼。

【旧 bash 的坑/巧思（保留原因）】
  · HARBOR_PROJECT MUST 随命令行显式传给远端：build-push.sh 写的是
    ${HARBOR_PROJECT:-itp}，本机 export 的变量不会自动进远端 shell——不传则
    会静默推到错误项目的 Harbor。远端命令里显式 `HARBOR_PROJECT=… ./build-push.sh`。
  · 判成功看远端输出的 digest: / Pushed，NEVER 只看退出码（bash 同样只判退出码，
    看 digest 是给人工核对的纪律，输出已回放供人工判读）。
  · JAR 选取：`find -maxdepth 1 -name "<服务>-*.jar" ! *-original/-sources/-javadoc
    | xargs -0 ls -t | head -1` = 按 mtime 取最新。坑：GNU xargs 对空输入也会跑
    一次 `ls -t`（误列当前目录）；BSD xargs（macOS）不跑。Python 显式空判，
    不依赖 xargs 行为。
  · 版本号提取：文件名去 .jar 后缀、再去 "<服务>-" 前缀；去不掉（= 仍等于原文件名）
    或为空即报「无法提取版本号」。原样保留。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 return 1；Python 按契约抛 OpsError——缺 --yes 6、
     用法 2、服务部署失败汇总后 5（EXEC）、其余执行失败 5。
  2. 远端 build-push 输出：bash 的 remote_exec 是 stdio 直通（实时流式）；Python
     骨架 core.remote.remote_exec 为抓输出设计（capture_output），故为「完成后
     回放 stdout/stderr」——文本内容一致，时序不同（长构建期间无实时反馈）。
  3. 远端命令的服务名/版本号经 remote_quote_args 转义（bash 直接拼裸参数；
     正常模块名二者等价，含 shell 元字符时 Python 更安全）。
  4. 未知命令：bash 打印 usage 到 stdout 后 return 1；Python 同样打 usage 到
     stdout，退出码按契约为 2（USAGE），与 cloudos 域同口径。
  5. scp 进度/远端 stderr 在 --json 下回放至 stderr（stdout 留给 JSON 信封）。
"""
from __future__ import annotations

import sys
import time
from pathlib import Path

from ..core import envx, remote, ui
from ..core.execx import need_cmd, repo_root, require_yes, run
from ..core.ui import context
from ..errors import EXIT_EXEC, EXIT_USAGE, OpsError

# ---------------------------------------------------------------- 全量清单（单一真源）
# 语义 = 仓库里可构建推送的模块全集，不是生产部署清单（见头注，NEVER 擅自「对齐」）。
ALL_SERVICES = [
    # ---- 原清单中经确认存在且可部署的（保持原有相对顺序，勿随意调换）----
    "account-server",
    "acc-secure-server",
    "acc-security-server",
    "acc-es-server",
    "collect-ticket-server",
    "collect-pay-server",
    "pay-sign-server",
    "ticket-server",
    "industry-data-server",
    "fep-app-server",
    "fep-dev-server",
    "gate-txn-pay-server",
    "blacklist-server",
    "key-server",
    "para-server",
    # ---- 2026-09-16 补齐：根 pom 中存在、有 kubernetes-maven-plugin，但原清单遗漏的 ----
    "card-pool-server",
    "face-pay-server",
    "daily-ticket-server",
    "trans-query-server",
    "fep-alipay-server",
    "fep-acc-server",
    "alipay-account-server",
    "alipay-pay-sign-server",
    "recon-server",
]

# ---- 颜色（对应 bash lib/core.sh：仅 stdout 是 TTY 时上色）----
if sys.stdout.isatty():
    _C_GREEN = "\033[0;32m"
    _C_RED = "\033[0;31m"
    _C_YELLOW = "\033[1;33m"
    _C_CYAN = "\033[0;36m"
    _C_BLUE = "\033[0;34m"
    _C_NC = "\033[0m"
else:
    _C_GREEN = _C_RED = _C_YELLOW = _C_CYAN = _C_BLUE = _C_NC = ""


# ---------------------------------------------------------------- 输出小工具
def _info(text: str) -> None:
    """bash log_info 等价 —— 旧 core.sh 的 log_info 落 **stdout**，为 stdout 字节兼容同走 stdout。"""
    ui.out(f"{_C_GREEN}[INFO]{_C_NC} {text}")


def _step(text: str) -> None:
    """bash log_step 等价（同样落 stdout）。"""
    ui.out(f"{_C_CYAN}[STEP]{_C_NC} {text}")


def _warn(text: str) -> None:
    """bash log_warn 等价（stderr）。"""
    ui.log_warn(text)


def _error(text: str) -> None:
    """bash log_error 等价（stderr）。"""
    ui.log_error(text)


def _echoe(text: str) -> None:
    """bash `echo -e` 等价（仅 \\n 转义会被展开，deploy-all 全部输出用它）。"""
    ui.out(text.replace("\\n", "\n"))


def _out_raw(text: str) -> None:
    """远端 stdout 回放：平时原样落 stdout；--json 下转 stderr（stdout 留给信封）。"""
    if not text:
        return
    if context().json_mode:
        sys.stderr.write(text)
    else:
        sys.stdout.write(text)
    sys.stdout.flush()


def _err_raw(text: str) -> None:
    """远端/scp stderr 回放（bash 同样落终端 stderr）。"""
    if text:
        sys.stderr.write(text)
        sys.stderr.flush()


# ---------------------------------------------------------------- 用法
def _help(env: envx.Env) -> str:
    remote_host = env.remote_host
    remote_dir = env.remote_dir
    n = len(ALL_SERVICES)
    return f"""\
itp-ops.sh k8s —— 镜像构建推送（本机 jar → {remote_host}:{remote_dir} → Harbor）

用法: itp-ops.sh k8s <命令> [服务...] [--yes]

命令
  deploy [服务...]              单/多服务部署；不带参数 = 全量 {n} 个。
                               某个失败继续下一个，末尾汇总（成功/失败/总计）。
  deploy-all [服务...]          串行全量部署；不带参数 = 全量 {n} 个。
                               ⚠️ 失败即中断后续（依赖有序部署时用这条），末尾带耗时汇总。
  services                      打印全量清单（单一真源，共 {n} 个）
  help                          本帮助

示例
  itp-ops.sh k8s deploy fep-dev-server --yes        # 只部署一个
  itp-ops.sh k8s deploy acc-es-server ticket-server --yes
  itp-ops.sh k8s deploy-all --yes                   # 有序全量，失败即断
  itp-ops.sh k8s services                           # 看清单

说明
  · deploy / deploy-all 是生产写操作（推 Harbor + 远端 build-push），
    缺 --yes 直接拒绝，与 cloudos 域同一套门。
  · JAR 从 <repo>/<服务>/target/ 取最新可执行包（排除 *-original/-sources/-javadoc.jar），
    版本号从 JAR 文件名提取；target 目录不存在即该服务失败。
  · 远端执行：scp jar → ssh {remote_host} "cd {remote_dir} && ./build-push.sh <服务> <版本>"。
  · 判成功看远端输出的 digest: / Pushed，NEVER 只看退出码。"""


# ---------------------------------------------------------------- 版本号提取（原样保留）
def _extract_version(jar_name: str, service_name: str) -> str:
    version = jar_name[:-4] if jar_name.endswith(".jar") else jar_name
    prefix = f"{service_name}-"
    if version.startswith(prefix):
        version = version[len(prefix):]
    return version


def _latest_jar(target: Path, service: str) -> Path | None:
    """target 下取最新可执行包（排除 *-original/-sources/-javadoc.jar，按 mtime）。"""
    suffixes = ("-original.jar", "-sources.jar", "-javadoc.jar")
    cands = [
        p for p in target.iterdir()
        if p.is_file() and not p.is_symlink()
        and p.name.startswith(f"{service}-") and p.name.endswith(".jar")
        and not p.name.endswith(suffixes)
    ]
    if not cands:
        return None
    return max(cands, key=lambda p: p.stat().st_mtime)


# ---------------------------------------------------------------- 单服务部署（原 deploy_service）
def _deploy_service(service: str, env: envx.Env) -> str | None:
    """部署单个服务；成功返回版本号，失败返回 None（失败不抛——deploy 语义是继续下一个）。"""
    _step(f"▶️  处理服务: {service}")

    target_dir = repo_root() / service / "target"

    # 1. 检查 target 目录
    if not target_dir.is_dir():
        _error(f"target 目录不存在: {target_dir}")
        return None

    # 2. 查找最新 JAR（排除 non-runnable 包）
    jar = _latest_jar(target_dir, service)
    if jar is None:
        _error(f"未找到匹配的JAR: {target_dir}/{service}-*.jar")
        return None

    jar_filename = jar.name
    version = _extract_version(jar_filename, service)

    # 3. 版本号安全校验
    if not version or version == jar_filename:
        _error(f"无法从 {jar_filename} 中提取版本号!")
        return None

    _info(f"📄 JAR文件: {jar_filename}")
    _info(f"🏷️  版本号:  {version}")

    # 4. SCP 上传
    _info(f"⬆️  正在上传到 {env.remote_host}:{env.remote_dir}/ ...")
    r = run(
        ["scp", str(jar), f"{env.remote_host}:{env.remote_dir}/"],
        what=f"scp {jar_filename}", check=False,
    )
    _err_raw(r.stderr)
    if r.returncode != 0:
        _error(f"上传失败: {jar_filename}")
        return None

    # 5. SSH 远程构建推送
    # ⚠ HARBOR_PROJECT MUST 随命令行显式传给远端（build-push.sh 写的是
    #   ${HARBOR_PROJECT:-itp}，本机环境变量不会自动进远端 shell）。
    _info(f"🔨 正在远程执行: HARBOR_PROJECT={env.harbor_project} ./build-push.sh {service} {version}")
    remote_cmd = (
        f"cd {env.remote_dir} && HARBOR_PROJECT={env.harbor_project} "
        f"./build-push.sh {remote.remote_quote_args([service, version])}"
    )
    rr = remote.remote_exec(remote_cmd)
    _out_raw(rr.stdout)
    _err_raw(rr.stderr)
    if rr.returncode != 0:
        _error(f"构建推送失败: {service}:{version}")
        return None

    _info(f"✅ {service}:{version} 部署完成!")
    return version


# ---------------------------------------------------------------- deploy（失败继续 + 汇总）
def _deploy(args: list[str], env: envx.Env) -> int:
    require_yes("k8s deploy（推 Harbor + 远端构建）")

    if not args:
        deploy_list = list(ALL_SERVICES)
        _info(f"📦 未指定参数，将部署全部 {len(deploy_list)} 个服务...")
    else:
        deploy_list = list(args)
        _info(f"📦 指定部署 {len(deploy_list)} 个服务: {' '.join(deploy_list)}")

    ui.out("==========================================")

    success_count = 0
    fail_count = 0
    deployed: list[str] = []
    failed: list[str] = []

    for svc in deploy_list:
        ui.out("")
        version = _deploy_service(svc, env)
        if version is not None:
            success_count += 1
            deployed.append(f"{svc}:{version}")
        else:
            _warn(f"服务 {svc} 部署失败，继续下一个...")
            fail_count += 1
            failed.append(svc)

    ui.out("")
    ui.out("==========================================")
    _info(f"🎉 部署结束! 成功: {success_count}, 失败: {fail_count}, 总计: {len(deploy_list)}")
    if fail_count > 0:
        _warn(f"有 {fail_count} 个服务部署失败，请检查上方日志")
        raise OpsError(
            f"k8s deploy 失败：成功 {success_count}，失败 {fail_count}，总计 {len(deploy_list)}",
            EXIT_EXEC,
        )
    ui.result({
        "deployed": deployed,
        "failed": failed,
        "success": success_count,
        "fail": fail_count,
        "total": len(deploy_list),
    })
    return 0


# ---------------------------------------------------------------- deploy-all（失败即断 + 耗时）
def _deploy_all(args: list[str], env: envx.Env) -> int:
    require_yes("k8s deploy-all（推 Harbor + 远端构建）")

    if args:
        services = list(args)
        mode = "指定部署"
    else:
        services = list(ALL_SERVICES)
        mode = "全量部署(预定义列表)"

    total = len(services)
    success_count = 0
    fail_count = 0
    # bash 的 FAILED_LIST 每项前带一个空格（含首项），原文如此，原样保留
    failed_list = ""
    deployed: list[str] = []
    failed: list[str] = []

    _echoe(f"{_C_BLUE}==========================================")
    _echoe(f"🚀 开始串行部署 | 模式: {mode}")
    _echoe(f"📋 服务列表({total}): {' '.join(services)}")
    _echoe(f"=========================================={_C_NC}\\n")

    start_time = int(time.time())

    for service in services:
        _echoe(f"{_C_YELLOW}------------------------------------------{_C_NC}")
        _echoe(f"{_C_BLUE}[STEP] ▶️  ({success_count}/{total}) 正在部署: {service}{_C_NC}")
        _echoe(f"{_C_YELLOW}------------------------------------------{_C_NC}")

        version = _deploy_service(service, env)
        if version is not None:
            success_count += 1
            deployed.append(f"{service}:{version}")
            _echoe(f"\\n{_C_GREEN}[INFO] ✅ {service} 部署成功!{_C_NC}\\n")
        else:
            fail_count += 1
            failed.append(service)
            failed_list = f"{failed_list} {service}"
            _echoe(f"\\n{_C_RED}[ERROR] ❌ {service} 部署失败! 终止后续部署.{_C_NC}\\n")
            break

    elapsed = int(time.time()) - start_time

    _echoe(f"{_C_BLUE}==========================================")
    _echoe("📊 部署汇总报告")
    _echoe(f"=========================================={_C_NC}")
    _echoe(f"⏱️  总耗时: {elapsed}s")
    _echoe(f"✅ 成功: {_C_GREEN}{success_count}{_C_NC}")
    _echoe(f"❌ 失败: {_C_RED}{fail_count}{_C_NC}")
    _echoe(f"📦 总计: {total}")

    if fail_count > 0:
        _echoe(f"\\n{_C_RED}⚠️  失败的服务:{failed_list}{_C_NC}")
        _echoe(f"{_C_YELLOW}💡 修复后可单独重跑: itp-ops.sh k8s deploy{failed_list} --yes{_C_NC}")
        raise OpsError(
            f"k8s deploy-all 失败：成功 {success_count}，失败 {fail_count}"
            f"（失败服务:{failed_list}）",
            EXIT_EXEC,
        )
    ui.result({
        "deployed": deployed,
        "failed": failed,
        "success": success_count,
        "fail": fail_count,
        "total": total,
        "elapsed_seconds": elapsed,
    })
    return 0


# ---------------------------------------------------------------- CATALOG
CATALOG = {
    "name": "k8s",
    "summary": "镜像构建推送（本机 jar → 跳板 → Harbor；只对测试环境可用）",
    "default_env": "test",
    "env_note": (
        "只对测试可用：红岛跳板 hd-linux 无 /home/java 构建目录与 build-push.sh"
        "（2026-10-26 实测远端只有 podman 没有 docker）；红岛发版走 cloudos image build"
        "（平台 x86 节点打镜像）。NEVER 用本域打红岛。"
    ),
    "subcommands": [
        {
            "name": "deploy",
            "usage": "k8s deploy [服务...] --yes",
            "summary": "单/多服务部署；不带参数 = 全量清单。某个失败继续下一个，末尾汇总（成功/失败/总计）。",
            "args": [
                {"name": "服务", "required": False,
                 "desc": "仓库模块目录名，可多个；缺省 = 全量 ALL_SERVICES"},
            ],
            "writes": True,
            "example": "itp-ops.sh k8s deploy fep-dev-server --yes",
            "stdout_human": "📄 JAR文件: … | ✅ fep-dev-server:1.2.43 部署完成! | 🎉 部署结束! 成功: 1, 失败: 0, 总计: 1",
            "json_data": {
                "deployed": ["fep-dev-server:1.2.43"],
                "failed": [],
                "success": 1,
                "fail": 0,
                "total": 1,
            },
        },
        {
            "name": "deploy-all",
            "usage": "k8s deploy-all [服务...] --yes",
            "summary": "串行全量部署；不带参数 = 全量清单。失败即中断后续（依赖有序部署时用这条），末尾带耗时汇总。",
            "args": [
                {"name": "服务", "required": False,
                 "desc": "仓库模块目录名，可多个；缺省 = 全量 ALL_SERVICES"},
            ],
            "writes": True,
            "example": "itp-ops.sh k8s deploy-all --yes",
            "stdout_human": "🚀 开始串行部署 | 模式: 全量部署(预定义列表) | 📊 部署汇总报告 | ⏱️  总耗时: Ns",
            "json_data": {
                "deployed": ["account-server:1.2.43", "…"],
                "failed": [],
                "success": 24,
                "fail": 0,
                "total": 24,
                "elapsed_seconds": 600,
            },
        },
        {
            "name": "services",
            "usage": "k8s services",
            "summary": "打印全量清单（单一真源：可构建推送的模块全集，不是生产部署清单）",
            "args": [],
            "writes": False,
            "example": "itp-ops.sh k8s services",
            "stdout_human": "account-server\\nacc-secure-server\\n…（每行一个，共 24 个）",
            "json_data": {"services": ["account-server", "acc-secure-server", "…"]},
        },
    ],
}


# ---------------------------------------------------------------- 域入口
def main(args: list[str]) -> int:
    need_cmd("ssh", "scp")
    # 默认 test，且本域实际只对测试可用（见头注「环境限制」）：红岛跳板 hd-linux
    # 上既无 /home/java 也无 build-push.sh——红岛发版走 cloudos image，NEVER 用本域打红岛。
    env = envx.init_env("test")
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "help", "-h", "--help"):
        ui.out(_help(env))
        return 0
    if sub == "services":
        ui.result({"services": list(ALL_SERVICES)})
        ui.out("\n".join(ALL_SERVICES))
        return 0
    if sub == "deploy":
        envx.banner(f"推送到 {env.remote_host}:{env.remote_dir}（Harbor 项目 {env.harbor_project}）")
        return _deploy(rest, env)
    if sub == "deploy-all":
        envx.banner(f"全量推送到 {env.remote_host}:{env.remote_dir}（Harbor 项目 {env.harbor_project}）")
        return _deploy_all(rest, env)

    ui.out(_help(env))
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)
