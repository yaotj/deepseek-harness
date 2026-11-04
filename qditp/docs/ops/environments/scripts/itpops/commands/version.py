"""itpops · version 域 —— 版本号计算 + 版本载体 bump（本地，不依赖环境/远端）。

子命令：
  next <ver>     求下一版（算法真源 core/version.py）
  debug          调试包哨兵 0.0.0.1
  bump [...]     pom.xml / package.json 就地 bump（原 scripts/bump-version.py，2026-11-04 收编）
"""
from __future__ import annotations

from pathlib import Path

from ..core import ui
from ..core.ui import context
from ..core.version import debug_version, next_version
from ..errors import EXIT_USAGE, OpsError
from ..versioning import bump

CATALOG = {
    "name": "version",
    "summary": "版本号计算与版本载体 bump（前端/后端统一规则）",
    "default_env": None,
    "env_note": "环境无关（本地计算/改文件，不打远端）",
    "subcommands": [
        {
            "name": "next",
            "usage": "version next <当前版本>",
            "summary": "末位 +1；任一分段 >99 向高位进位（1.2.99 → 1.3.0；99.99 → 1.0.0）",
            "args": [{"name": "当前版本", "required": True}],
            "writes": False,
            "example": "itp-ops.sh version next 1.2.42",
            "stdout_human": "1.2.43",
            "json_data": {"current": "1.2.42", "next": "1.2.43"},
        },
        {
            "name": "debug",
            "usage": "version debug",
            "summary": "打印 CloudOS 调试包哨兵版本 0.0.0.1（不占用发布序列）",
            "args": [],
            "writes": False,
            "example": "itp-ops.sh version debug",
            "stdout_human": "0.0.0.1",
            "json_data": {"version": "0.0.0.1"},
        },
        {
            "name": "bump",
            "usage": "version bump (--all-pom | --dir <目录>) [--kind pom|packagejson] [--bump normal|debug] [--dry-run] [--spring-boot <ver>]",
            "summary": "就地 bump pom.xml / web/package.json 的版本字段（只改版本，不构建不部署；--dry-run 只打印）",
            "args": [
                {"name": "--all-pom", "desc": "对仓库内全部后端模块 pom 做 bump"},
                {"name": "--dir <目录>", "desc": "指定目录（含 pom.xml 或 package.json）"},
                {"name": "--kind pom|packagejson", "desc": "目录下两种文件都有时显式指定"},
                {"name": "--bump normal|debug", "desc": "normal=末位+1；debug=追加/递增第四段（默认 normal）"},
                {"name": "--dry-run", "desc": "只打印不改文件（全局 -n/--dry-run 等价）"},
                {"name": "--spring-boot <ver>", "desc": "pom parent 版本（默认 3.2.6）"},
            ],
            "writes": False,
            "example": "itp-ops.sh version bump --dir account-server --dry-run",
            "stdout_human": "  ✓ …/pom.xml: 2.1.4 → 2.1.5  (sb=3.2.6)",
            "json_data": {"bumped": {"account-server": "2.1.5"}, "dry_run": False},
        },
    ],
}

_HELP = """\
itp-ops version —— 版本号规则与载体 bump（前端/后端统一，2026-11-02 裁定）

用法:
  itp-ops.sh version next <当前版本>   # 末位 +1；任一分段 >99 向高位进位（1.2.99 → 1.3.0、1.99.99 → 2.0.0）
  itp-ops.sh version debug            # 打印 CloudOS 调试包哨兵版本 0.0.0.1
  itp-ops.sh version bump [选项]      # 就地 bump pom.xml / package.json

bump 选项:
  --all-pom                 仓库内全部后端模块 pom
  --dir <目录>              指定目录（含 pom.xml 或 package.json）
  --kind pom|packagejson    目录下两种文件都有时显式指定
  --bump normal|debug       normal=末位 +1（默认）；debug=追加/递增第四段（1.2.44.2 → 1.2.44.3）
  --dry-run                 只打印不改文件（等价全局 -n）
  --spring-boot <ver>       pom parent 版本，默认 3.2.6

说明: bump 只改版本字段（pom 的 <version> + parent、package.json 的 version），
      NEVER 跑构建/部署；改动 MUST 进 git diff 审查。"""


def _bump(rest: list[str]) -> int:
    all_pom = False
    target_dir: str | None = None
    kind: str | None = None
    mode = "normal"
    dry_run = False
    spring_boot = "3.2.6"

    i = 0
    while i < len(rest):
        a = rest[i]
        if a in ("-h", "--help", "help"):
            ui.out(_HELP)
            return 0
        if a == "--all-pom":
            all_pom = True
            i += 1
        elif a == "--dry-run":
            dry_run = True
            i += 1
        elif a in ("--dir", "--kind", "--bump", "--spring-boot"):
            if i + 1 >= len(rest):
                raise OpsError(f"{a} 需要值", EXIT_USAGE)
            val = rest[i + 1]
            if a == "--dir":
                target_dir = val
            elif a == "--kind":
                if val not in ("pom", "packagejson"):
                    raise OpsError(f"--kind 只支持 pom/packagejson，收到：{val}", EXIT_USAGE)
                kind = val
            elif a == "--bump":
                if val not in ("normal", "debug"):
                    raise OpsError(f"--bump 只支持 normal/debug，收到：{val}", EXIT_USAGE)
                mode = val
            else:
                spring_boot = val
            i += 2
        else:
            raise OpsError(f"未知参数：{a}（version bump -h 查看用法）", EXIT_USAGE)

    if all_pom and target_dir:
        raise OpsError("--all-pom 与 --dir 只能二选一", EXIT_USAGE)
    if not all_pom and not target_dir:
        raise OpsError("必须指定 --all-pom 或 --dir（version bump -h 查看用法）", EXIT_USAGE)

    targets = bump.module_dirs() if all_pom else [Path(target_dir).expanduser()]
    for d in targets:
        if not d.is_dir():
            raise OpsError(f"目录不存在：{d}", EXIT_USAGE)

    dry_run = dry_run or context().dry_run
    new_versions = bump.run(targets, kind=kind, mode=mode, dry_run=dry_run,
                            spring_boot=spring_boot)
    for line in bump.render_summary(new_versions, dry_run):
        ui.out(line)
    ui.result({"bumped": new_versions, "dry_run": dry_run, "mode": mode})
    return 0


def main(args: list[str]) -> int:
    sub = args[0] if args else ""
    rest = args[1:]

    if sub in ("", "-h", "--help", "help"):
        ui.out(_HELP)
        return 0
    if sub == "next":
        if not rest:
            raise OpsError("version next 需要当前版本号", EXIT_USAGE)
        current = rest[0]
        nxt = next_version(current)
        ui.result({"current": current, "next": nxt})
        ui.out(nxt)  # stdout 与 bash 版字节兼容：只打印下一版本
        return 0
    if sub == "debug":
        v = debug_version()
        ui.result({"version": v})
        ui.out(v)
        return 0
    if sub == "bump":
        return _bump(rest)

    raise OpsError(f"未知子命令：{sub}（支持 next <version> / debug / bump）", EXIT_USAGE)
