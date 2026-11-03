"""远端执行助手 —— lib/remote.sh 的 Python 移植。跳板机按环境选，本机不直连集群。

【为什么走 ssh 而不是本机 kubectl】
  本机 kubeconfig 没有 ITP 集群凭据（AGENTS.md §7），所有集群操作 MUST
  经跳板：ssh <REMOTE_HOST> "kubectl ..."。REMOTE_HOST 由 envx 按 --env 设定。

【TTY 与否（MUST 分清，别合并）】
  remote_exec      非交互：一次性命令、抓输出。BatchMode 友好。
  remote_exec_tty  强制伪终端：-f 实时跟踪 / Ctrl+C / 颜色 / 逐行刷新。
                   NEVER 给跟踪命令去掉 -t，否则不刷新且 Ctrl+C 杀不掉对端。

【参数转义】
  远端命令是拼字符串执行的，NEVER 直接拼裸参数——服务名/关键字含空格或 shell
  元字符时会断。用 remote_quote_args（shlex.quote）逐项转义后再拼。
"""
from __future__ import annotations

import shlex
import subprocess

from ..errors import EXIT_EXEC, EXIT_TRANSPORT, OpsError
from . import envx, ui

SSH_TRANSPORT_EXIT = 255  # ssh 自身失败（连不上/认证失败）；远端命令非 0 是别的码


def remote_host() -> str:
    return envx.current().remote_host


def remote_exec(remote_cmd: str) -> subprocess.CompletedProcess:
    """非交互执行；remote_cmd 为完整远端命令串。返回 CompletedProcess（不替调用方判成败）。"""
    host = remote_host()
    ui.debug(f"ssh {host} {remote_cmd}")
    return subprocess.run(
        ["ssh", "-o", "ConnectTimeout=8", host, remote_cmd],
        capture_output=True, text=True,
    )


def remote_exec_tty(remote_cmd: str) -> subprocess.CompletedProcess:
    """强制 TTY 执行（跟踪/交互场景）。stdio 继承给当前终端，实时刷新。"""
    host = remote_host()
    ui.debug(f"ssh -t {host} {remote_cmd}")
    return subprocess.run(
        ["ssh", "-t", "-o", "ConnectTimeout=8", host, remote_cmd],
    )


def remote_reachable() -> bool:
    """连通性预检（BatchMode，不弹密码提示——不通就是不通，快速失败）。"""
    host = remote_host()
    r = subprocess.run(
        ["ssh", "-o", "ConnectTimeout=6", "-o", "BatchMode=yes", host, "true"],
        capture_output=True, text=True,
    )
    return r.returncode == 0


def remote_quote_args(args: list[str]) -> str:
    """把本地参数安全转义成可拼进远端命令串的形式（对应 bash printf %q）。"""
    return " ".join(shlex.quote(a) for a in args)


def run_remote_ok(remote_cmd: str, *, what: str = "") -> subprocess.CompletedProcess:
    """执行远端命令并强制成功：ssh 连不上 → EXIT_TRANSPORT；远端非 0 → EXIT_EXEC。

    大多数域命令应使用本函数；需要自判非 0 语义（如探活）时用 remote_exec。
    """
    try:
        r = remote_exec(remote_cmd)
    except OSError as exc:
        raise OpsError(f"ssh 执行失败：{exc}", EXIT_TRANSPORT) from exc
    if r.returncode == SSH_TRANSPORT_EXIT:
        raise OpsError(
            f"跳板机不可达：{remote_host()}（ssh exit 255）"
            + (f"；目标：{what}" if what else ""),
            EXIT_TRANSPORT,
        )
    if r.returncode != 0:
        detail = (r.stderr or r.stdout or "").strip().splitlines()
        tail = " | ".join(detail[-3:]) if detail else f"exit {r.returncode}"
        raise OpsError(
            f"远端命令失败（{what or remote_cmd}）：{tail}", EXIT_EXEC
        )
    return r
