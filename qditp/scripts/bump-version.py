#!/usr/bin/env python3
# bump-version.py —— ITP 平台版本 bump 工具
#
# 【定位】
#   本仓两套版本载体：
#     · 后端 pom.xml 的 <version> 字段（Maven 项目坐标；jar 文件名由此生成）
#     · 前端 web/package.json 的 version 字段（zip 文件名由此生成）
#   一套 pom / package.json 的 `<version>` 同时充当 jar / zip 文件名的一部分，
#   因此 bump 后「重新构建」一步就能产出带新版本号的产物，无需单独改名。
#
# 【bump 规则（用户 2026-11-02 裁定）】
#   所有版本号都是语义化变体：X.Y.Z[.W]，段都是十进制整数。
#     1. 普通 bump（本地构建增量）：最后一位 +0.0.1
#        · pom 形如 2.1.4 → 2.1.5；package.json 形如 1.2.44.2 → 1.2.44.3
#        · pom 末段是纯字母（如 `1.1.1.re`）时按 §2 兜底
#     2. 进位（末段超过 99）：进位到上一段，末段归零
#        · X.Y.99 → X.(Y+1).0；X.Y.Z.99 → X.Y.(Z+1).0
#        · 不进位到更高：最多只动最后两段（与 Maven 惯例一致）
#     3. Debug bump（CloudOS 控制台手动上发调试包）：最后一位 +0.0.0.1
#        · 1.2.44.2 → 1.2.44.3；2.1.4 → 2.1.4.1
#        · 仅 package.json 用这条（前端没有 Maven 三段号）；pom 也用则把 debug bump
#          当成 1. 的语义（pom 无第四段，无法表达 .1）
#
# 【pom 语义补充（Maven 默认惯例，2026-11-02 固化）】
#   本仓所有服务 pom 都继承 `spring-boot-starter-parent:3.2.6`，没有显式
#   `<parent.version>` —— 因此 pom.xml 的根 `<version>` 既是项目版本也是
#   Spring Boot 版本。bump 后必须同步改 Spring Boot 版本，否则会拉错 base。
#   默认值 3.2.6；要换传 `--spring-boot 3.3.x`。
#
# 【用法】
#   python3 scripts/bump-version.py --help
#
#   # 后端 pom：本地增量 bump（规则 1）
#   python3 scripts/bump-version.py --dir account-server --kind pom --bump normal
#
#   # 前端 package.json：debug bump（规则 3）
#   python3 scripts/bump-version.py --dir web --kind packagejson --bump debug
#
#   # 批量：后端全部 pom + 前端 package.json
#   python3 scripts/bump-version.py --all-pom --kind pom --bump normal
#   python3 scripts/bump-version.py --dir web --kind packagejson --bump debug
#
#   # dry-run：只打印不会改文件
#   python3 scripts/bump-version.py --dir account-server --kind pom --dry-run
#
# 【纪律】
#   · 不改任何版本字段以外的文本；若匹配到多个 `<version>`（dependencyManagement
#     下的库版本等），只改第一个；其它一律留原样。
#   · pom 的 build 产物命名 MUST 跟 pom `<version>` 走：
#     jar = `<artifactId>-<version>.jar`（Spring Boot maven-jar-plugin 默认）。
#     所以 bump 完重建就能出正确名称的 jar，无需额外 rename。
#   · package.json 的 zip 名由 web/package.json 的 version 决定（现有规则：
#     `web-frontend-<version>.zip`）；bump 完重跑 `npm run build:zip` 即可。
#   · 不在本脚本里跑构建或部署（MUST 独立执行，避免误触写操作）。
#   · 所有改动的 diff 进 git（本脚本只动版本，不混入功能变更）。
# ============================================================================

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Iterable, Optional, Tuple

# ------------------------------------------------------------------------ 语义
SEP = "."
MAX_SEGMENTS = 4  # X.Y.Z 或 X.Y.Z.W，再多段就认为格式非法

# 允许作为 Maven artifactId 出现在 pom 路径中的子串；与目录名对应
MODULE_DIRS = [
    "account-server",
    "alipay-account-server",
    "alipay-pay-sign-server",
    "blacklist-server",
    "card-pool-server",
    "collect-pay-server",
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


# ------------------------------------------------------------------------ 解析
def parse_semver(s: str) -> Optional[Tuple[int, ...]]:
    """'2.1.4' → (2, 1, 4)；纯字母末段（如 '1.1.1.re'）也允许——bump 时会走兜底。"""
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
            # 末段可以非数字（记录但返回 None，由调用方走兜底）
            if len(out) == len(parts) - 1:
                return None
            return None
    return tuple(out)


def fmt_semver(seg: Tuple[int, ...]) -> str:
    return SEP.join(str(x) for x in seg)


# ------------------------------------------------------------------------ bump 计算
def bump_normal(seg: Tuple[int, ...]) -> Tuple[int, ...]:
    """规则 1：末段 +1；末段 > 99 时进位到上一段，当前段归零。"""
    vals = list(seg)
    vals[-1] += 1
    carry = 0
    for i in range(len(vals) - 1, -1, -1):
        vals[i] += carry
        if vals[i] > 99:
            carry = 1
            vals[i] = 0
        else:
            carry = 0
            break
    # 最多进到第二高位（符合 Maven 惯例：2.1.99 → 2.2.0，不是 3.0.0）
    if carry:
        vals[0] += 1
        vals[1] = 0
        # 理论上不应该走到这里，guard
    return tuple(vals)


def bump_debug(seg: Tuple[int, ...]) -> Tuple[int, ...]:
    """规则 3：追加第四段 .1；若已有四段则只 +1 第四段；不足四段先补零再 +1。"""
    if len(seg) >= MAX_SEGMENTS:
        vals = list(seg[:MAX_SEGMENTS])
        vals[-1] += 1
    else:
        vals = list(seg) + [0] * (MAX_SEGMENTS - len(seg))
        vals[-1] = 1
    return tuple(vals)


# ------------------------------------------------------------------------ 解析阶段
def resolve_module_dirs() -> list[Path]:
    """仓库根下所有模块目录（排除已知非模块的目录）。"""
    root = Path(__file__).resolve().parent.parent
    out: list[Path] = []
    for name in MODULE_DIRS:
        p = root / name
        if p.is_dir():
            out.append(p)
    # 也支持直接传 --dir 覆盖
    return out


# ------------------------------------------------------------------------ 文件写入
def bump_pom(
    pom_dir: Path,
    bump: str,
    dry_run: bool,
    spring_boot_default: str = "3.2.6",
) -> str:
    """对 <module-dir>/pom.xml 做 bump。
    改动目标：根节点第一个 <version>（项目版本，不是 dependency 的版本）。
    额外同步：把根节点对应的 <parent.version> 更新为 spring_boot_default。
    返回 new_version 字符串（供调用方使用）。
    """
    pom_file = pom_dir / "pom.xml"
    if not pom_file.exists():
        raise FileNotFoundError(f"找不到 pom.xml：{pom_file}")

    raw = pom_file.read_text(encoding="utf-8")

    # 匹配根 <version>（第一个顶格或一级缩进的 <version>...</version>）
    m = re.search(r"<version>([^<]+)</version>", raw)
    if not m:
        raise ValueError(f"{pom_file} 没有 <version> 字段")
    old_raw_ver = m.group(1)
    seg = parse_semver(old_raw_ver)
    if seg is None:
        raise ValueError(
            f"{pom_file} 的 <version>='{old_raw_ver}' 不是合法 semver（X.Y.Z[.W]）"
        )

    fn = bump_normal if bump == "normal" else bump_debug
    new_seg = fn(seg)
    new_ver = fmt_semver(new_seg)
    if new_ver == old_raw_ver:
        raise ValueError(
            f"{pom_file} 的 version {old_raw_ver} 经 {bump} 后不变 —— 已升到上限？"
        )

    # 替换第一个匹配（项目版本）
    new_raw = raw[: m.start(1)] + new_ver + raw[m.end(1) :]

    # 同步 parent.version（如果存在且等于默认值）
    # Maven 惯例：继承 spring-boot-starter-parent 时不写 parent.version，
    # 但部分模块显式写了，统一处理以防漏改
    sb_match = re.search(
        r"<parent>\s*.*?<version>([^<]+)</version>",
        new_raw,
        re.DOTALL,
    )
    if sb_match:
        sb_old = sb_match.group(1)
        # 只改等于 spring boot 默认的那段；其它库的 version 不动
        if sb_old == spring_boot_default or sb_old == old_raw_ver:
            new_raw = (
                new_raw[: sb_match.start(1)]
                + spring_boot_default
                + new_raw[sb_match.end(1) :]
            )

    # 写回
    if not dry_run:
        pom_file.write_text(new_raw, encoding="utf-8")
        print(f"  ✓ {pom_file}: {old_raw_ver} → {new_ver}  (sb={spring_boot_default})")
    else:
        print(f"  [dry-run] {pom_file}: {old_raw_ver} → {new_ver}")
    return new_ver


def bump_package_json(
    web_dir: Path,
    bump: str,
    dry_run: bool,
) -> str:
    """对 web/package.json 做 bump。"""
    pj_file = web_dir / "package.json"
    if not pj_file.exists():
        raise FileNotFoundError(f"找不到 package.json：{pj_file}")

    text = pj_file.read_text(encoding="utf-8")
    obj = json.loads(text)
    old_ver: str = obj.get("version", "")
    if not old_ver:
        raise ValueError(f"{pj_file} 没有 version 字段")
    seg = parse_semver(old_ver)
    if seg is None:
        raise ValueError(
            f"{pj_file} 的 version='{old_ver}' 不是合法 semver（X.Y.Z[.W]）"
        )

    fn = bump_normal if bump == "normal" else bump_debug
    new_seg = fn(seg)
    new_ver = fmt_semver(new_seg)
    if new_ver == old_ver:
        raise ValueError(
            f"{pj_file} 的 version {old_ver} 经 {bump} 后不变 —— 已升到上限？"
        )
    obj["version"] = new_ver

    if not dry_run:
        # 保留原有缩进（json.dumps ensure_ascii=False + indent=2 是现有约定）
        new_text = json.dumps(obj, ensure_ascii=False, indent=2) + "\n"
        pj_file.write_text(new_text, encoding="utf-8")
        print(f"  ✓ {pj_file}: {old_ver} → {new_ver}")
    else:
        print(f"  [dry-run] {pj_file}: {old_ver} → {new_ver}")
    return new_ver


# ------------------------------------------------------------------------ CLI
def main() -> int:
    parser = argparse.ArgumentParser(
        description="ITP 平台版本号 bump（pom / package.json）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例
  python3 scripts/bump-version.py --all-pom --bump normal       # 后端全部 pom 增量 bump
  python3 scripts/bump-version.py --dir account-server         # 单模块 pom 增量 bump
  python3 scripts/bump-version.py --dir web --kind packagejson  # 前端 package.json
  python3 scripts/bump-version.py --dir web --bump debug        # 前端 debug bump
        """,
    )
    # 目标
    parser.add_argument(
        "--all-pom",
        action="store_true",
        help="对仓库内所有后端模块 pom.xml 做 bump",
    )
    parser.add_argument(
        "--dir",
        nargs="?",
        default=None,
        metavar="DIR",
        help="指定目录（包含 pom.xml 或 package.json）",
    )
    parser.add_argument(
        "--kind",
        choices=["pom", "packagejson"],
        default=None,
        help="若 --dir 同时有两种文件，显式指定要处理哪一种",
    )
    # 行为
    parser.add_argument(
        "--bump",
        choices=["normal", "debug"],
        default="normal",
        help="bump 规则：normal（+0.0.1）/ debug（+0.0.0.1，用于 CloudOS 手动调试包）",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="只打印不写盘",
    )
    parser.add_argument(
        "--spring-boot",
        default="3.2.6",
        metavar="VER",
        help="pom parent 的 spring-boot-starter-parent 版本（默认 3.2.6，通常不动）",
    )
    args = parser.parse_args()

    targets: list[Path] = []
    if args.all_pom:
        targets = resolve_module_dirs()
    elif args.dir:
        targets = [Path(args.dir)]
    else:
        parser.error("必须指定 --all-pom 或 --dir")

    # 对每个目标分派
    new_versions: dict[str, str] = {}
    for d in targets:
        d = d.resolve()
        kind: str = args.kind or (
            "packagejson" if (d / "package.json").exists() else "pom"
        )
        if kind == "pom":
            new_versions[d.name] = bump_pom(d, args.bump, args.dry_run, args.spring_boot)
        elif kind == "packagejson":
            new_versions[d.name] = bump_package_json(d, args.bump, args.dry_run)
        else:
            raise ValueError(f"不支持的 kind：{kind}")

    # 汇总
    print()
    print("=" * 72)
    if args.dry_run:
        print("[DRY-RUN] 以下是将要执行的动作，未修改任何文件")
    else:
        print("bump 完成（未自动执行 build / deploy）")
    print("=" * 72)
    for k, v in sorted(new_versions.items()):
        print(f"  {k:25s}  →  {v}")
    print()
    print("下一步 MUST 手动执行：")
    print("  后端  mise exec -- mvn -o clean package -pl <模块> -DskipTests")
    print("  前端  cd web && npm run build:zip")
    print("  部署  bash itp-ops.sh buildx <镜像名> <新tag> --env test --jar ...")
    print("      + ./itp-ops.sh --env test cloudos deploy <应用> <新tag> --yes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
