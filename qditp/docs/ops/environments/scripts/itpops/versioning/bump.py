"""版本载体 bump（原 scripts/bump-version.py，2026-11-04 并入 itp-ops）。

【两套版本载体】
  · 后端 pom.xml 的 `<version>`（jar 文件名由此生成）
  · 前端 web/package.json 的 `version`（zip 文件名由此生成）

【规则（用户 2026-11-02 裁定）】
  1. normal：末位 +1；末段 > 99 进位到上一段（2.1.99 → 2.2.0；1.2.44.99 → 1.2.45.0）
  2. debug ：追加第四段（1.2.44.2 → 1.2.44.3；2.1.4 → 2.1.4.1），用于 CloudOS 手动调试包
  3. pom 的 parent（spring-boot-starter-parent）版本同步改（默认 3.2.6）

【纪律】只改版本字段，不混功能变更；不跑构建/部署；所有改动进 git diff 可审。
"""
from __future__ import annotations

import json
import re
from pathlib import Path

from ..core import ui
from ..core.runtime import repo_root
from ..errors import EXIT_EXEC, EXIT_PRECONDITION, EXIT_USAGE, OpsError

SEP = "."
MAX_SEGMENTS = 4

# 参与 --all-pom 的模块目录（仓库根下的后端模块）
MODULE_DIRS = [
    "account-server",
    "alipay-account-server",
    "alipay-pay-sign-server",
    "blacklist-server",
    "card-pool-server",
    "daily-ticket-server",
    "face-pay-server",
    "fep-acc-server",
    "fep-alipay-server",
    "fep-app-server",
    "fep-dev-server",
    "gate-txn-pay-server",
    "industry-data-server",
    "key-server",
    "para-server",
    "pay-sign-server",
    "recon-server",
    "ticket-server",
    "trans-query-server",
]


# ---------------------------------------------------------------- 语义
def parse_semver(s: str) -> tuple[int, ...] | None:
    """'2.1.4' → (2,1,4)；非法（含字母段、空段、超 4 段）→ None。"""
    parts = s.strip().split(SEP)
    if not parts or len(parts) > MAX_SEGMENTS:
        return None
    out: list[int] = []
    for p in parts:
        if not p:
            return None
        try:
            out.append(int(p))
        except ValueError:
            return None
    return tuple(out)


def fmt_semver(seg: tuple[int, ...]) -> str:
    return SEP.join(str(x) for x in seg)


def bump_normal(seg: tuple[int, ...]) -> tuple[int, ...]:
    """末段 +1；>99 进位到上一段（最多动末两段，与 Maven 惯例一致）。"""
    vals = list(seg)
    vals[-1] += 1
    carry = 0
    for i in range(len(vals) - 1, -1, -1):
        vals[i] += carry
        if vals[i] > 99:
            carry, vals[i] = 1, 0
        else:
            carry = 0
            break
    if carry:                      # 理论上到不了（末两段最大 99.99 → 100.0 已进位）
        vals[0] += 1
        vals[1] = 0
    return tuple(vals)


def bump_debug(seg: tuple[int, ...]) -> tuple[int, ...]:
    """追加/递增第四段。"""
    if len(seg) >= MAX_SEGMENTS:
        vals = list(seg[:MAX_SEGMENTS])
        vals[-1] += 1
    else:
        vals = list(seg) + [0] * (MAX_SEGMENTS - len(seg))
        vals[-1] = 1
    return tuple(vals)


def _apply(seg: tuple[int, ...], kind: str) -> tuple[int, ...]:
    fn = bump_normal if kind == "normal" else bump_debug
    return fn(seg)


# ---------------------------------------------------------------- 单文件
def bump_pom(pom_dir: Path, kind: str, dry_run: bool, spring_boot: str = "3.2.6") -> str:
    pom_file = pom_dir / "pom.xml"
    if not pom_file.exists():
        raise OpsError(f"找不到 pom.xml：{pom_file}", EXIT_PRECONDITION)
    # newline="" 保留原始换行符：仓库里 alipay-pay-sign-server / trans-query-server 的 pom 是 CRLF，
    # 默认 read_text/write_text 走通用换行会把整份文件悄悄改成 LF，SVN/git 上表现为「整个文件被改」。
    # 2026-11-20 发 ticket / trans-query / alipay 三个服务时实测踩到，只能手工再转回 CRLF。
    with pom_file.open("r", encoding="utf-8", newline="") as fh:
        raw = fh.read()
    m = re.search(r"<version>([^<]+)</version>", raw)
    if not m:
        raise OpsError(f"{pom_file} 没有 <version> 字段", EXIT_EXEC)
    old = m.group(1)
    seg = parse_semver(old)
    if seg is None:
        raise OpsError(f"{pom_file} 的 <version>='{old}' 不是合法 semver（X.Y.Z[.W]）", EXIT_EXEC)

    new = fmt_semver(_apply(seg, kind))
    if new == old:
        raise OpsError(f"{pom_file} 的 version {old} 经 {kind} 后不变 —— 已升到上限？", EXIT_EXEC)

    new_raw = raw[:m.start(1)] + new + raw[m.end(1):]
    sb = re.search(r"<parent>\s*.*?<version>([^<]+)</version>", new_raw, re.DOTALL)
    if sb and sb.group(1) in (spring_boot, old):
        new_raw = new_raw[:sb.start(1)] + spring_boot + new_raw[sb.end(1):]

    if dry_run:
        ui.out(f"  [dry-run] {pom_file}: {old} → {new}")
    else:
        with pom_file.open("w", encoding="utf-8", newline="") as fh:
            fh.write(new_raw)
        ui.out(f"  ✓ {pom_file}: {old} → {new}  (sb={spring_boot})")
    return new


def bump_package_json(web_dir: Path, kind: str, dry_run: bool) -> str:
    pj = web_dir / "package.json"
    if not pj.exists():
        raise OpsError(f"找不到 package.json：{pj}", EXIT_PRECONDITION)
    obj = json.loads(pj.read_text(encoding="utf-8"))
    old = obj.get("version", "")
    if not old:
        raise OpsError(f"{pj} 没有 version 字段", EXIT_EXEC)
    seg = parse_semver(old)
    if seg is None:
        raise OpsError(f"{pj} 的 version='{old}' 不是合法 semver（X.Y.Z[.W]）", EXIT_EXEC)

    new = fmt_semver(_apply(seg, kind))
    if new == old:
        raise OpsError(f"{pj} 的 version {old} 经 {kind} 后不变 —— 已升到上限？", EXIT_EXEC)
    obj["version"] = new

    if dry_run:
        ui.out(f"  [dry-run] {pj}: {old} → {new}")
    else:
        pj.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        ui.out(f"  ✓ {pj}: {old} → {new}")
    return new


# ---------------------------------------------------------------- 目标解析与执行
def module_dirs() -> list[Path]:
    root = repo_root()
    return [root / n for n in MODULE_DIRS if (root / n).is_dir()]


def detect_kind(d: Path) -> str:
    return "packagejson" if (d / "package.json").exists() else "pom"


def run(targets: list[Path], *, kind: str | None, mode: str, dry_run: bool,
        spring_boot: str) -> dict[str, str]:
    new_versions: dict[str, str] = {}
    for d in targets:
        d = d.resolve()
        k = kind or detect_kind(d)
        if k == "pom":
            new_versions[d.name] = bump_pom(d, mode, dry_run, spring_boot)
        elif k == "packagejson":
            new_versions[d.name] = bump_package_json(d, mode, dry_run)
        else:  # pragma: no cover - 参数已限定
            raise OpsError(f"不支持的 kind：{k}", EXIT_USAGE)
    return new_versions


def render_summary(new_versions: dict[str, str], dry_run: bool) -> list[str]:
    lines = ["", "=" * 72]
    lines.append("[DRY-RUN] 以下是将要执行的动作，未修改任何文件" if dry_run
                 else "bump 完成（未自动执行 build / deploy）")
    lines.append("=" * 72)
    lines += [f"  {k:25s}  →  {v}" for k, v in sorted(new_versions.items())]
    lines += [
        "",
        "下一步 MUST 手动执行：",
        "  后端  mise exec -- mvn -o clean package -pl <模块> -DskipTests",
        "  前端  cd web && npm run build:zip",
        "  部署  itp-ops.sh buildx <模块名> <新tag> --env test（模块无自有 Dockerfile 时加",
        "        --dockerfile docs/ops/environments/scripts/buildx.Dockerfile）",
        "      + itp-ops.sh --env test cloudos deploy <应用> <新tag> --yes",
    ]
    return lines
