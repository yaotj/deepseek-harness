"""itpops · log 域 —— K8s 服务日志查看与监控。

移植自 commands/log.sh（bash，237 行）：原 ops/klog.sh（日志查看代理）+
ops/klog-watcher.sh（Pod 重启自动重挂监控）二合一。命令面与旧 bash 逐字兼容；
help 文案原样拷贝（`log` / `log help` / `log -h` 与 legacy stdout diff 为空）。

【TTY 纪律（MUST）】
  tail 一律走 ssh -t（remote_exec_tty）：-f 实时跟踪 / Ctrl+C / 颜色 /
  逐行刷新都依赖伪终端。NEVER 改成非交互 ssh，NEVER 去掉 -t。

【参数转义纪律（MUST）】
  远端命令是拼字符串执行的，NEVER 直接拼用户参数——服务名/关键字含空格或
  shell 元字符时会断。用 remote_quote_args（shlex.quote）逐项转义后再拼。

【旧 bash 的坑/巧思（保留原因）】
  · KLOG_CONTAINER 以前只在帮助里「能用」，实际 ssh 不转发本地环境变量——
    现显式拼进远端命令前缀才真正生效（bash 已修，原样保留）。
  · watcher 状态文件写入格式 `svc=key`，旧查找用 `grep "^svc|"` 永远匹配不到
    ⇒ 每轮全量杀/起重挂。已改为按写入格式查找（bash 已修，原样保留）。
  · 杀监控按 PID 记录 + cmdline 校验杀进程树（bash + ssh），防 PID 复用误杀，
    也防 remote tail -F 漏网累积（bash 已修，原样保留）。
  · 「klog 搜不到某行」≠「没发生」——只看容器内日志文件尾部窗口，Pod 重启即丢，
    全量检索 MUST ssh 跳板后逐 Pod grep /home/javaapp/app/logs/<POD_NAME>/<app>.log。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 一律 return 1（tail 则透传 ssh 退出码）；Python 按契约
     抛 OpsError——缺 --json 支持 2、watch 未知参数 2、未知命令 2、ssh 不可达 4、
     远端 tail 非 0 5。
  2. 修复 watch_start_monitor 的漏子命令 bug：bash 起监控用
     `itp-ops.sh log "$svc" "-${WATCH_HISTORY}f"`——log 域首参是子命令，"fep-app"
     走「未知命令」分支立即退出，监控从未真正跑起来。Python 起的是
     `log tail <svc> -<历史>f`；kill 时的 cmdline 校验相应改为匹配 "tail <svc> "
     （仍能区分 fep-app / fep-app-server，且兼容 python/bash 两种入口的 cmdline）。
  3. 修复 WATCH_NS 求值顺序 bug：bash 在 source 时展开
     `WATCH_NS="${KLOG_NS:-${K8S_NS:-itp}}"`，此时 env profile 尚未初始化，
     --env hongdao 的 K8S_NS=itpb 到不了它（头注所述「由 profile 提供」实际未生效，
     红岛会静默 watch 测试命名空间 itp）。Python 按头注意图在运行时取值：
     KLOG_NS 环境变量 > envx profile 的 k8s_ns（K8S_NS 仍可覆盖 profile）。
  4. 监控子进程的常驻语义换实现：bash 靠非交互后台（& 自动忽略 SIGINT/SIGQUIT）
     + nohup（忽略 SIGHUP）；Python 用 start_new_session（独立会话，Ctrl+C
     波及不到）+ nohup。效果一致：Ctrl+C 退出 watcher 不杀监控。
  5. tail 的输出是远端 TTY 直通，任何形态都装不进单条 JSON 信封，故 tail 整个
     子命令 json:false（--json 下 raise；比「仅 -f 不支持」更严格，理由：信封唯一性）。
     watch 同理（长驻守护 + 直通输出）。
  6. 环境横幅按契约用 envx.banner（stderr，[环境] 行）；bash 的 banner 同样落 stderr，
     stdout 不受影响。
"""
from __future__ import annotations

import os
import shlex
import signal
import subprocess
import time
from pathlib import Path

from ..core import environments as envx, remote, ui
from ..core.runtime import need_cmd, scripts_dir
from ..core.ui import context
from ..errors import EXIT_EXEC, EXIT_TRANSPORT, EXIT_USAGE, OpsError

# 目标服务列表（旧 watcher 原样保留）
WATCH_SERVICES = [
    "alipay-pay-sign-server",
    "alipay-account-server",
    "fep-alipay-server",
    "fep-alipay",
    "fep-app-server",
    "fep-app",
    "fep-dev-server",
    "ticket-server",
    "para-server",
    "blacklist-server",
    "pay-sign-server",
    "industry-data-server",
    "daily-ticket-server",
    "gate-txn-pay-server",
    "key-server",
    "collect-pay",
    "collect-pay-server",
    "account",
    "security-server",
    "es-server",
]

_REMOTE_SCRIPT_DEFAULT = "/home/java/k8s-logview.sh"
_WATCH_HISTORY_DEFAULT = "1000"
_WATCH_STATE_DEFAULT = "/tmp/klog-watcher-state.txt"


# ---------------------------------------------------------------- 配置（运行时求值）
def _remote_script() -> str:
    return os.environ.get("ITP_KLOG_SCRIPT") or _REMOTE_SCRIPT_DEFAULT


def _watch_ns() -> str:
    """KLOG_NS 环境变量 > profile K8S_NS（修 bash source 时求值的错位，见头注差异 3）。"""
    return os.environ.get("KLOG_NS") or envx.current().k8s_ns


def _watch_history() -> str:
    return os.environ.get("KLOG_HISTORY") or _WATCH_HISTORY_DEFAULT


def _watch_state_file() -> Path:
    return Path(os.environ.get("KLOG_WATCH_STATE") or _WATCH_STATE_DEFAULT)


# ---------------------------------------------------------------- 输出小工具
def _info(text: str) -> None:
    """bash log_info 等价（落 stdout，stdout 字节兼容）。"""
    ui.out(f"[INFO] {text}")


def _ts() -> str:
    return time.strftime("%Y-%m-%d %H:%M:%S")


# ---------------------------------------------------------------- 用法
def _help(env: envx.Env) -> str:
    remote_host = env.remote_host
    return f"""\
itp-ops.sh log —— K8s 服务日志查看 / 重启自动重挂监控（经 {remote_host} 跳板）

用法: itp-ops.sh log <命令> [参数...]

命令
  tail <服务名|JAR前缀> [关键字...] [-f] [行数]
                          查看日志（默认最近 100 行）。参数顺序不敏感，
                          数字=行数，-f=跟踪，-2000f=历史 2000 行+跟踪，
                          其余按关键字（可多个，空格关键字加引号）。
  watch [--once]          监控守护：每 30s 巡检目标 Pod，重启/就绪态变化即
                          重挂 tail -f 监控（nohup 后台，Ctrl+C 退出 watcher
                          不会杀监控——这是特性，监控要的就是常驻）。
                          --once = 只跑一轮（调试用）。

示例
  itp-ops.sh log tail fep-app                  # 最近 100 行
  itp-ops.sh log tail fep-app 500              # 最近 500 行
  itp-ops.sh log tail fep-app ERROR            # 含 ERROR 的最近 100 行
  itp-ops.sh log tail fep-app -f               # ⭐ 实时跟踪（Ctrl+C 退出）
  itp-ops.sh log tail fep-app Exception -f     # ⭐ 实时跟踪含 Exception
  itp-ops.sh log tail fep-app "user login" -f  # ⭐ 带空格的关键字
  itp-ops.sh log tail fep-app -2000f           # ⭐ 最近 2000 行历史
  itp-ops.sh log tail fep-app ERROR Exception -2000f   # ⭐ 多关键字+历史
  KLOG_CONTAINER=nginx itp-ops.sh log tail gateway    # 指定多容器 Pod 的容器
  itp-ops.sh log watch                          # 启动监控守护

⚠️ 局限（与旧脚本一致，别当成失传）
  · 只看容器内日志文件的尾部窗口；Pod 一重启日志目录就消失。
    全量检索 MUST：ssh {remote_host} 后逐 Pod grep 日志文件
    （目录形如 /home/javaapp/app/logs/<POD_NAME>/<app>.log）。
  · 「klog 搜不到某行」≠「没发生」——重启即丢，见 docs/domain/decisions.md。"""


# ---------------------------------------------------------------- tail（原 klog.sh）
def _tail(args: list[str], env: envx.Env) -> int:
    if not args or args[0] in ("-h", "--help"):
        ui.out(_help(env))
        return 0
    if context().json_mode:
        raise OpsError("该命令不支持 --json", EXIT_USAGE)

    # KLOG_CONTAINER：ssh 不转发本地环境变量，显式拼成远端命令前缀才生效
    prefix = ""
    container = os.environ.get("KLOG_CONTAINER") or ""
    if container:
        prefix = f"KLOG_CONTAINER={shlex.quote(container)} "
        _info(f"指定容器: {container}（经远端命令前缀生效）")

    # printf %q 安全转义所有参数，NEVER 改成直接拼用户参数
    safe_args = remote.remote_quote_args(args)
    remote_cmd = f"{prefix}bash {shlex.quote(_remote_script())} {safe_args}"

    # -t：强制伪终端，是 -f 跟踪 / Ctrl+C / 颜色 / 逐行刷新的前提，NEVER 去掉
    r = remote.remote_exec_tty(remote_cmd)
    if r.returncode == remote.SSH_TRANSPORT_EXIT:
        raise OpsError(
            f"跳板机不可达：{remote.remote_host()}（ssh exit 255）", EXIT_TRANSPORT
        )
    if r.returncode != 0:
        raise OpsError(f"远端日志命令失败（exit {r.returncode}）", EXIT_EXEC)
    return 0


# ---------------------------------------------------------------- watch（原 klog-watcher.sh）
def _watch_get_pod(ns: str, svc: str) -> str:
    r = remote.remote_exec(
        f"kubectl get pods -n {ns} --no-headers -l app={svc} "
        f"-o custom-columns='NAME:.metadata.name'"
    )
    lines = r.stdout.splitlines()
    return lines[0].strip() if lines else ""


def _watch_get_container_ready(ns: str, pod: str) -> str:
    r = remote.remote_exec(
        f"kubectl get pod -n {ns} {remote.remote_quote_args([pod])} "
        f"-o jsonpath='{{.status.containerStatuses[0].ready}}'"
    )
    return r.stdout.strip()


def _watch_get_restart_count(ns: str, pod: str) -> str:
    r = remote.remote_exec(
        f"kubectl get pod -n {ns} {remote.remote_quote_args([pod])} "
        f"-o jsonpath='{{.status.containerStatuses[0].restartCount}}'"
    )
    return r.stdout.strip()


def _kill_tree(pid: int) -> None:
    """杀进程树：先杀子（ssh）再杀自己，避免 remote tail -F 漏网累积。"""
    try:
        r = subprocess.run(["pgrep", "-P", str(pid)], capture_output=True, text=True)
    except OSError:
        r = None
    if r is not None and r.returncode == 0:
        for child in r.stdout.split():
            try:
                _kill_tree(int(child))
            except ValueError:
                pass
    try:
        os.kill(pid, signal.SIGTERM)
    except OSError:
        pass


def _watch_kill_monitor(svc: str, state: Path) -> None:
    """只杀自己启动过的监控（PID 记录 + cmdline 校验，防 PID 复用误杀）。"""
    pid_file = Path(f"{state}.d/{svc}.pid")
    if not pid_file.is_file():
        return
    try:
        pid_text = pid_file.read_text().strip()
    except OSError:
        pid_text = ""
    if not pid_text:
        pid_file.unlink(missing_ok=True)
        return
    args = ""
    try:
        r = subprocess.run(["ps", "-p", pid_text, "-o", "args="], capture_output=True, text=True)
        if r.returncode == 0:
            args = r.stdout
    except OSError:
        pass
    # bash 校验 *"log ${svc}"*（对应旧的漏子命令 spawn 形态，见头注差异 2）：
    # 现 spawn 是 `log tail <svc> -<历史>f`，改查 "tail <svc> "（带尾空格防
    # fep-app 误配 fep-app-server）。
    if f"tail {svc} " in args:
        ui.out(f"[{_ts()}] 终止旧监控: svc={svc} pid={pid_text}")
        try:
            _kill_tree(int(pid_text))
        except ValueError:
            pass
        time.sleep(1)
    pid_file.unlink(missing_ok=True)


def _watch_start_monitor(svc: str, pod: str, history: str, state: Path) -> None:
    ui.out(f"[{_ts()}] 启动监控: svc={svc} pod={pod}")
    state_d = Path(f"{state}.d")
    state_d.mkdir(parents=True, exist_ok=True)
    entry = scripts_dir() / "itp-ops.sh"
    # nohup + 独立会话 ≈ bash 的 `nohup … &`（非交互后台忽略 SIGINT/SIGQUIT +
    # nohup 忽略 SIGHUP）：Ctrl+C 退出 watcher 不会杀监控（特性，见 help）。
    # ⚠ 必须带 `tail` 子命令（bash 版漏了，见头注差异 2）。
    p = subprocess.Popen(
        ["nohup", "bash", str(entry), "log", "tail", svc, f"-{history}f"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    (state_d / f"{svc}.pid").write_text(str(p.pid))


def _read_state(state: Path, svc: str) -> str:
    """状态文件一行一条 `svc=key`，查找 MUST 按同一格式（旧版这里写错，见头注）。"""
    try:
        lines = state.read_text().splitlines()
    except OSError:
        return ""
    prefix = f"{svc}="
    hits = [ln[len(prefix):] for ln in lines if ln.startswith(prefix)]
    return hits[-1] if hits else ""


def _update_state(state: Path, svc: str, key: str) -> None:
    """更新状态文件（重建过滤，避免 sed -i 的 BSD/GNU 差异）。"""
    prefix = f"{svc}="
    try:
        keep = [ln for ln in state.read_text().splitlines() if not ln.startswith(prefix)]
    except OSError:
        keep = []
    tmp = Path(f"{state}.tmp")
    tmp.write_text("".join(f"{ln}\n" for ln in keep))
    tmp.replace(state)
    with state.open("a") as fh:
        fh.write(f"{svc}={key}\n")


def _watch_pass(ns: str, history: str, state: Path) -> None:
    for svc in WATCH_SERVICES:
        pod = _watch_get_pod(ns, svc)
        if not pod:
            continue

        restart = _watch_get_restart_count(ns, pod)
        ready = _watch_get_container_ready(ns, pod)
        key = f"{svc}|{pod}|{restart}|{ready}"

        old_key = _read_state(state, svc)
        if key != old_key:
            # 状态变化：先杀旧监控，ready 再起新监控
            _watch_kill_monitor(svc, state)
            if ready == "true":
                _watch_start_monitor(svc, pod, history, state)
            _update_state(state, svc, key)


def _watch(args: list[str]) -> int:
    once = False
    for a in args:
        if a == "--once":
            once = True
        elif a in ("-h", "--help"):
            ui.out(_help(envx.current()))
            return 0
        else:
            raise OpsError(f"watch 未知参数：{a}", EXIT_USAGE)
    if context().json_mode:
        raise OpsError("该命令不支持 --json", EXIT_USAGE)

    ns = _watch_ns()
    history = _watch_history()
    state = _watch_state_file()
    state.parent.mkdir(parents=True, exist_ok=True)
    Path(f"{state}.d").mkdir(parents=True, exist_ok=True)
    state.write_text("")

    ui.out(f"监控守护启动：ns={ns} history={history} 服务数={len(WATCH_SERVICES)}")
    ui.out(f"状态文件：{state}（PID 记录：{state}.d/）")
    if once:
        ui.out("模式：--once（只跑一轮）")

    while True:
        _watch_pass(ns, history, state)
        if once:
            break
        time.sleep(30)
    return 0


# ---------------------------------------------------------------- CATALOG
CATALOG = {
    "name": "log",
    "summary": "K8s 服务日志查看 / 重启自动重挂监控（经跳板）",
    "default_env": "test",
    "env_note": (
        "默认 test；红岛可显式 --env hongdao（watch 走 kubectl 可用）。"
        "tail 依赖远端 /home/java/k8s-logview.sh，该脚本在 hd-linux 尚未部署 → "
        "红岛 tail 暂不可用（用 watch 或 ssh 后 kubectl logs）。"
    ),
    "subcommands": [
        {
            "name": "tail",
            "usage": "log tail <服务名|JAR前缀> [关键字...] [-f] [行数]",
            "summary": (
                "查看日志（默认最近 100 行）。参数顺序不敏感：数字=行数，-f=跟踪，"
                "-2000f=历史 2000 行+跟踪，其余=关键字（可多个）。输出为远端 TTY 直通。"
            ),
            "args": [
                {"name": "服务名|JAR前缀", "required": True},
                {"name": "关键字", "required": False, "desc": "可多个，空格关键字加引号"},
                {"name": "-f", "required": False, "desc": "实时跟踪（Ctrl+C 退出）"},
                {"name": "行数", "required": False, "desc": "缺省 100；-2000f 形态=历史行数+跟踪"},
            ],
            "writes": False,
            "json": False,
            "example": "itp-ops.sh log tail fep-app Exception -f",
            "stdout_human": "（远端 k8s-logview.sh 输出直通终端）",
        },
        {
            "name": "watch",
            "usage": "log watch [--once]",
            "summary": (
                "监控守护：每 30s 巡检目标 Pod，重启/就绪态变化即重挂 tail -f 监控"
                "（nohup 后台，Ctrl+C 退出 watcher 不杀监控）。--once=只跑一轮（调试）。"
                "本地写状态文件/起后台监控，非生产写操作（无 --yes 门）。"
            ),
            "args": [
                {"name": "--once", "required": False, "desc": "只跑一轮（调试用）"},
            ],
            "writes": False,
            "json": False,
            "example": "itp-ops.sh log watch --once",
            "stdout_human": "监控守护启动：ns=itp history=1000 服务数=20 | [时间] 启动监控: svc=… pod=…",
        },
    ],
}


# ---------------------------------------------------------------- 域入口
def main(args: list[str]) -> int:
    need_cmd("ssh")
    # 默认 test；红岛可显式 --env hongdao（ssh hd-linux + ns itpb，2026-10-26 已连通）：
    #   watch 走 kubectl，红岛可用；tail 依赖远端 /home/java/k8s-logview.sh，
    #   该脚本在 hd-linux 上尚不存在 → 红岛 tail 暂不可用（用 watch 或 ssh 后 kubectl logs）。
    env = envx.init_env("test")
    sub = args[0] if args else "tail"
    rest = args[1:]

    if sub in ("tail", ""):
        envx.banner(f"远端 {env.remote_host}:{_remote_script()}（ns={_watch_ns()}）")
        return _tail(rest, env)
    if sub == "watch":
        envx.banner(f"监控守护，命名空间 {_watch_ns()}")
        return _watch(rest)
    if sub in ("help", "-h", "--help"):
        ui.out(_help(env))
        return 0

    envx.banner("")
    ui.out(_help(env))
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)
