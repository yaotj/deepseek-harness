"""工作流执行器 —— 把多步 SOP 变成一条命令。

约定（与 itp-ops 全局契约对齐）：
  · 缺 --yes / 带 --dry-run 时**只列步骤**，一步都不执行（离线也能预演）；
  · 每一步都走统一入口 `python -m itpops`，不绕过全局参数 / JSON 信封 / 退出码；
  · 失败即断，已完成的步骤与变量写进 ui.result，便于机器上排查；
  · 红岛生产 MUST 显式 --allow-prod，否则直接拒绝。

步骤间变量：Step 的 argv 支持 `{version}` 等占位符，由 capture（正则）/ json_path
（从 -o json 信封取值）/ transform（next_version）在上一步产出，执行期填充。
"""
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass

from ..core import ui
from ..core.runtime import repo_root, scripts_dir
from ..core.version import next_version
from ..errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_USAGE, OpsError


@dataclass(frozen=True)
class Step:
    title: str
    argv: tuple[str, ...] = ()
    kind: str = "itpops"             # itpops | exec | derive
    expect: str | None = None        # stdout+stderr 必须包含的判据
    capture: str | None = None       # 正则第 1 组 → var
    json_path: str | None = None     # 从 -o json 信封按点路径取值 → var
    var: str | None = None
    transform: str | None = None     # next_version
    cwd: str | None = None
    write: bool = False              # 仅用于预演标注


@dataclass(frozen=True)
class Flow:
    name: str
    summary: str
    steps: tuple[Step, ...]
    notes: tuple[str, ...] = ()


def _dig(payload: object, path: str) -> str:
    cur = payload
    for part in path.split("."):
        if not isinstance(cur, dict) or part not in cur:
            raise OpsError(f"JSON 信封里取不到 {path}", EXIT_EXEC)
        cur = cur[part]
    return str(cur)


def _render(step: Step, variables: dict[str, str]) -> list[str]:
    try:
        return [p.format(**variables) for p in step.argv]
    except KeyError as exc:
        raise OpsError(f"步骤「{step.title}」缺少变量 {exc}", EXIT_USAGE) from exc


def _preview(step: Step, variables: dict[str, str]) -> list[str]:
    """预演时变量尚未产出，缺的占位符原样显示，不报错。"""
    out = []
    for p in step.argv:
        try:
            out.append(p.format(**variables))
        except (KeyError, IndexError):
            out.append(p)
    return out


def _command(step: Step, variables: dict[str, str]) -> list[str]:
    parts = _render(step, variables)
    if step.kind == "exec":
        return parts
    return [sys.executable, "-m", "itpops", *parts]


def run(flow: Flow, variables: dict[str, str]) -> int:
    ctx = ui.context()
    env = ctx.env or "test"
    if env == "hongdao" and not variables.get("allow_prod"):
        raise OpsError(
            "红岛是生产：flow 一键流程 MUST 显式加 --allow-prod（并确认低峰 + 回滚预案）",
            EXIT_PRECONDITION,
        )

    ui.out("===== 工作流 %s（%s）=====" % (flow.name, env))
    ui.out("  %s" % flow.summary)
    ui.out("")
    ui.out("  步骤清单（%d 步）：" % len(flow.steps))
    for i, step in enumerate(flow.steps, 1):
        tag = "写" if step.write else "读"
        ui.out("    %2d. [%s] %s" % (i, tag, step.title))
        if step.argv:
            ui.out("        %s" % " ".join(_preview(step, variables)))
        if step.expect:
            ui.out("        预期判据：%s" % step.expect)
        if step.capture:
            ui.out("        捕获变量：%s (正则 %r)" % (step.var, step.capture))
    for note in flow.notes:
        ui.out("  ⚠ " + note)

    if ctx.dry_run or not ctx.yes:
        ui.out("")
        ui.out("以上为一键流程预演，未执行任何步骤。确认执行请加 --yes"
               + ("；打红岛生产另需 --allow-prod" if env != "hongdao" else "") + "。")
        write_count = sum(1 for s in flow.steps if s.write)
        ui.out("写操作 %d 处，读操作 %d 处。" % (write_count, len(flow.steps) - write_count))
        ui.result({"flow": flow.name, "env": env, "dry_run": True,
                   "steps": [s.title for s in flow.steps],
                   "write_count": write_count, "read_count": len(flow.steps) - write_count})
        return 0

    variables = dict(variables)
    variables.setdefault("env", env)
    done: list[dict] = []
    envp = dict(os.environ)
    envp["PYTHONPATH"] = str(scripts_dir()) + os.pathsep + envp.get("PYTHONPATH", "")

    for i, step in enumerate(flow.steps, 1):
        ui.log_info("[%d/%d] %s" % (i, len(flow.steps), step.title))
        if step.kind == "derive":
            if step.transform == "next_version" and step.var:
                variables[step.var] = next_version(variables[step.var])
            done.append({"step": step.title, "rc": 0})
            continue

        result = subprocess.run(
            _command(step, variables), capture_output=True, text=True,
            cwd=step.cwd or str(repo_root()), env=envp,
        )
        # 子步骤输出一律转发到 stderr：-o json 时 stdout 只留本层信封
        for stream in (result.stdout, result.stderr):
            if stream and stream.strip():
                print(stream.rstrip(), file=sys.stderr)

        combined = (result.stdout or "") + (result.stderr or "")
        if result.returncode != 0:
            ui.result({"flow": flow.name, "env": env, "done": False,
                       "failed_step": step.title, "rc": result.returncode,
                       "completed": done, "variables": variables})
            raise OpsError("步骤「%s」失败（exit %d），流程已中断" % (step.title, result.returncode),
                           EXIT_EXEC)
        if step.expect and step.expect not in combined:
            ui.result({"flow": flow.name, "env": env, "done": False,
                       "failed_step": step.title, "expect": step.expect,
                       "completed": done, "variables": variables})
            raise OpsError("步骤「%s」缺少判据 %r，流程已中断（不要只看退出码）"
                           % (step.title, step.expect), EXIT_EXEC)

        if step.json_path and step.var:
            try:
                variables[step.var] = _dig(json.loads(result.stdout), step.json_path)
            except json.JSONDecodeError as exc:
                raise OpsError("步骤「%s」输出不是 JSON 信封：%s" % (step.title, exc),
                               EXIT_EXEC) from exc
        elif step.capture and step.var:
            m = re.search(step.capture, result.stdout or "")
            if not m:
                raise OpsError("步骤「%s」没能从输出捕获变量（正则 %r）"
                               % (step.title, step.capture), EXIT_EXEC)
            variables[step.var] = m.group(1)
        if step.transform == "next_version" and step.var:
            variables[step.var] = next_version(variables[step.var])
        done.append({"step": step.title, "rc": 0})

    ui.out("")
    ui.out("✓ 工作流 %s 完成（%d 步）" % (flow.name, len(done)))
    ui.result({"flow": flow.name, "env": env, "done": True,
               "steps": done, "variables": variables})
    return 0
