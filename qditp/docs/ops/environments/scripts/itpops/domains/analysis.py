"""itpops · analysis 域 —— 代码/文档静态分析（可重跑刷新文档数字）。

移植自 domains/analysis.sh（bash，59 行）。命令面与旧 bash 逐字兼容：子命令名、
参数、stdout 结果文本一致；help 文案原样拷贝（与 legacy stdout diff 为空）。

【旧 bash 的坑/巧思（原样保留原因）】
  1. 「cd 到仓库根再执行」是硬需求：count-endpoints.py historically 按「从仓库根
     运行」设计（相对路径读写 docs/），从别处跑会静默找不到文件或把输出写错地方。
     bash 版 `cd "$ITP_OPS_ROOT"` 后调 python3；Python 版用 subprocess 的 cwd=
     repo_root() 等价，NEVER 改成相对当前工作目录执行。
  2. 本域只做调度：count-endpoints.py 一行未改地继续当真源（可重跑的计数器），
     域层只负责 cwd、命令名稳定（endpoints）、usage / 依赖检查。
  3. interfaces / requirements 两个子命令在 bash 侧已下线（2026-10-26 合理性审计：
     extract_interfaces.py 依赖不存在的 API.md 一跑即崩、extract_requirements.py
     源目录全不存在空转输出 0 文档，连脚本一起删了）。bash 现状只剩 endpoints，
     Python 同样只注册 endpoints——`analysis interfaces` 与 bash 一致报未知命令。
  4. 环境无关：不调 envx.init_env / envx.banner（lib/env.sh 头注与 envx 契约均注明
     analysis 不调用）。打的是本机文件系统，不碰远端。

【与 bash 版的差异】（原则上零差异，以下均有理由）
  1. 失败退出码细分：bash 把 python3 的退出码原样透传（die 时 exit 1）；Python 按
     契约抛 OpsError——count-endpoints.py 的 argparse 用法错（rc=2）→ 用法 2（码值
     与 bash 一致），其它非 0 → 执行 5；仓库根断言失败 / 缺 python3 → 前置 3。
     成功路径 stdout 逐字节一致（脚本输出经 ui.out 原样转发，--json 时抑制、数据
     进信封 data）。
  2. 错误行前缀：cli 统一以「错误：」落 stderr（bash 是裸文本「未知命令：x」）。
"""
from __future__ import annotations

import os
import re
import subprocess
import sys

from ..core import ui
from ..core.execx import need_cmd, repo_root, scripts_dir
from ..errors import EXIT_EXEC, EXIT_USAGE, OpsError

# ---------------------------------------------------------------- 帮助（逐字拷贝自 analysis_usage）
_HELP = """\
itp-ops.sh analysis —— 代码/文档静态分析（从仓库根执行，输出写回 docs/）

用法: itp-ops.sh analysis <命令> [参数...]

命令
  endpoints <模块源码目录|.> [--csv]
                          方法级 HTTP 端点数统计（@Get/Post/Put/Delete/RequestMapping，
                          仅 *Controller.java，类级 @RequestMapping 不计）。
                          传 . = 全仓。--csv 额外输出 CSV。
                          典型：itp-ops.sh analysis endpoints account-server/src/main/java
  help                     本帮助

说明
  · 脚本在仓库根 cwd 下运行（相对路径读写在 docs/），
    本域自动 cd，NEVER 从子目录直接 python3 调它们。
  · 这是轻量静态分析，不解析条件编译/多 profile；数字随代码变动，
    改动接口后 MUST 重跑刷新文档。"""

_SUMMARY_RE = re.compile(r"方法级端点合计: (\d+)  （文件数 (\d+)）")


def _endpoints(rest: list[str]) -> int:
    root = repo_root()
    script = scripts_dir() / "analysis" / "count-endpoints.py"
    # bash analysis_run：cd "$ITP_OPS_ROOT" 后 python3 <scripts>/analysis/<script> "$@"
    try:
        r = subprocess.run(
            ["python3", str(script), *rest],
            cwd=str(root), capture_output=True, text=True,
        )
    except OSError as exc:
        raise OpsError(f"执行失败：{exc}", EXIT_EXEC) from exc

    # stdout 逐字节转发（bash 是继承 stdio 直出）；--json 模式下 ui.out 自动抑制
    out_text = r.stdout
    if out_text:
        ui.out(out_text[:-1] if out_text.endswith("\n") else out_text)
    if r.stderr:
        sys.stderr.write(r.stderr)
    if r.returncode != 0:
        # argparse 用法错 rc=2 → 码值与 bash 透传一致；其它非 0 细分为 EXEC（差异 1）
        if r.returncode == 2:
            raise OpsError("analysis endpoints 参数不对（见 count-endpoints.py 用法）", EXIT_USAGE)
        raise OpsError(f"count-endpoints.py 失败（exit {r.returncode}）", EXIT_EXEC)

    # 机器载荷：从汇总行解析（csv 模式没有汇总行，按行统计）
    root_arg = next((a for a in rest if not a.startswith("-")), "")
    if "--csv" in rest:
        rows = [ln for ln in out_text.splitlines() if ln]
        total = sum(int(ln.rsplit(",", 1)[1]) for ln in rows if "," in ln and ln.rsplit(",", 1)[1].isdigit())
        files = len(rows)
    else:
        m = _SUMMARY_RE.search(out_text)
        total, files = (int(m.group(1)), int(m.group(2))) if m else (0, 0)
    ui.result({"root": root_arg, "csv": "--csv" in rest, "total": total, "files": files})
    return 0


# ---------------------------------------------------------------- 域入口
CATALOG = {
    "name": "analysis",
    "summary": "代码/文档静态分析：方法级 HTTP 端点数统计（可重跑刷新文档数字）",
    "default_env": None,
    "env_note": "环境无关（本机静态分析，不调 envx、不打远端）",
    "subcommands": [
        {
            "name": "endpoints",
            "usage": "analysis endpoints <模块源码目录|.> [--csv]",
            "summary": "方法级 HTTP 端点数统计（@Get/Post/Put/Delete/RequestMapping，仅 *Controller.java，类级 @RequestMapping 不计）。传 . = 全仓；--csv 额外输出 CSV",
            "args": [{"name": "模块源码目录|.", "required": True}, {"name": "--csv", "required": False}],
            "writes": False,
            "example": "itp-ops.sh analysis endpoints account-server/src/main/java",
            "stdout_human": " 12  XxxController.java（含类级 1）\n…\n方法级端点合计: 42  （文件数 7）",
            "json_data": {"root": "account-server/src/main/java", "csv": False, "total": 42, "files": 7},
        },
    ],
}


def main(args: list[str]) -> int:
    need_cmd("python3")  # bash analysis_main 第一句（含 help 在内都要求）
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_HELP)
        return 0
    if sub == "endpoints":
        return _endpoints(rest)

    ui.out(_HELP)
    raise OpsError(f"未知命令：{sub}", EXIT_USAGE)
