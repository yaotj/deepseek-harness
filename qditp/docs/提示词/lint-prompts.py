#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""qd-itp 提示词健康检查（lint）

把 `AGENTS.md`（§5.3 工程纪律）里那些靠人/AI 自觉的约定，变成可机械执行的检查。
核心思想：**能用代码保证的，就不要写在提示词里指望模型记住。**

用法：
    python3 docs/提示词/lint-prompts.py [仓库根] [-v] [--fix] [--update-baseline] [--deep] [--no-skills]

  -v      打印全部 WARN（默认只打汇总 + 每类 3 条样本）
  --fix  自动删除「本处此前写 X 已过期」类纯历史补丁句（只删，不重写其余内容）
  --update-baseline  重新生成存量基线（清理了一批源码行号引用后 MUST 跑一次）
  --deep  额外把每条源码行号引用与源码树核对（约 13 秒；诊断用，不进提交门禁）
  --no-skills  跳过第三层（`~/.workbuddy/skills/qditp-*/SKILL.md`）检查

**覆盖提示词体系三层**：① 常驻层 `AGENTS.md`（§5.3 工程纪律 + 常驻预算）；
② 按需层 `docs/` 约束层文档（断链 / 补丁句 / 旧编号 / 行号引用基线）；
③ 技能层 `qditp-*`（同一套腐烂规则，但**不设存量基线**——按需加载、总量小，任何行号引用都算新增）。

分级：
  ERROR 退出码 1 —— 违反核心约定或引用不存在的文件，必然误导后续改动
  WARN  退出码 0 —— 需要人看一眼，不一定是问题

**存量基线机制**（`docs/提示词/lint-baseline.json`）：源码行号引用是存量债务，
逐条报 WARN 会刷屏 4000+ 条把人淹没。基线把「存量」与「新增」分开——
存量按文件汇总成一条 WARN；**基线外的新增引用报 ERROR**（违反「NEVER 新增行号引用」）。
清完一批存量后 MUST `--update-baseline`，否则后续新增仍然会被基线掩盖。

**只查「约束层」文档**（能指挥后续改动的那批）；
`docs/testing/`、`docs/reviews/`、`docs/history/`、甲方规格原文是记录与归档，
写「此前写 X 已作废」是合理叙述，不在检查范围。

新增检查项 MUST 在 CHECKS 里加一个函数，并在 docstring 写清判据。
"""

import json
import re
import sys
import time
from pathlib import Path

_args = [a for a in sys.argv[1:] if not a.startswith("-")]
ROOT = Path(_args[0] if _args else ".").resolve()
AGENTS = ROOT / "AGENTS.md"
BASELINE = ROOT / "docs" / "提示词" / "lint-baseline.json"

# `Xxx.java:123` 形式的源码行号引用（行号会漂，§5.3 禁令）
SOURCE_REF_RE = re.compile(r"[\w./-]+\.(?:java|xml|properties|yml|vue|ts):\d+")

# 已知已改号的 sys_job 编号（2026-09-21「200 以下全量重编号」后的旧值）
STALE_JOB_IDS = ["133", "134", "135", "265", "235", "109", "105", "122", "123"]

# 「本处此前写 X 已过期」这类补丁句的起手词（§5.3 明令禁止）
PATCH_RE = re.compile(
    r"(本处此前|本文件此前|本条此前|本行此前)[^。；\n]{0,80}"
    r"(已过期|已作废|NEVER 回退|是错的)"
)

# 约束层：能指挥后续改动的文档
CONSTRAINT_PREFIXES = (
    "docs/business/", "docs/domain/", "docs/ops/",
    "docs/architecture/", "docs/提示词/",
)


def is_constraint(rel: str) -> bool:
    if rel == "AGENTS.md":
        return True
    if rel.startswith("skill:"):
        return True
    return rel.startswith(CONSTRAINT_PREFIXES)


def check_contract_present(txt, rel, base=None):
    """AGENTS.md 必须包含「NEVER 追加补丁」这条核心约定（由 §5.3 承载）。"""
    if rel != "AGENTS.md":
        return []
    if "NEVER 追加补丁" not in txt:
        return [("ERROR", f"{rel}  缺少「事实过期 NEVER 追加补丁」核心约定")]
    return [("OK", f"{rel}  核心约定在位")]


def check_no_patches(txt, rel, base=None):
    """§5.3：事实过期 MUST 直接改旧值，NEVER 追加「本处此前写 X 已过期」补丁。"""
    if not is_constraint(rel):
        return []
    hits = []
    for i, line in enumerate(txt.splitlines(), 1):
        for m in PATCH_RE.finditer(line):
            frag = line[max(0, m.start()-40):m.end()+40]
            # 与 fix_patches 同一套保护：§5.3 里描述反模式的原句不是补丁
            if "写 X 已过期" in frag or "反模式" in frag:
                continue
            hits.append(("ERROR", f"{rel}:{i}  补丁句「{m.group(0)[:60]}」"))
    return hits


# 常驻预算阈值（2026-10-13 按「最佳实践上限 + 少量余量」重设）
# 依据：ETH Zurich 2026 实测仓库级上下文文件越长成功率越低、推理成本越高（>20%）；
# 业界共识 ≤300 行、每行都注入每一次会话。当前实际 365 行 / 35594 字符，
# 阈值只留约 4% 增长空间，逼内容继续下沉到 docs/ 与技能。
BUDGET_CHARS = 37000
BUDGET_LINES = 380
BUDGET_NEVER_LINES = 80
# 技能（第三层，`~/.workbuddy/skills/qditp-*/SKILL.md`）：按需加载、不受常驻预算约束，
# 但单篇超长说明该拆 references/ 子目录（渐进式披露同样适用于技能内部）。
SKILL_LINES_SOFT = 350
# 第三层：用户级技能目录（在仓库外，与约束层文档同一套腐烂规则）
SKILLS_ROOT = Path.home() / ".workbuddy" / "skills"


def check_skill_budget(txt, rel, base=None):
    """技能单篇长度：超长宜把细节拆到 references/（SKILL.md 只留流程与判据）。"""
    if not rel.startswith("skill:"):
        return []
    lines = txt.count("\n") + 1
    if lines > SKILL_LINES_SOFT:
        return [("WARN", f"{rel}  {lines} 行（> {SKILL_LINES_SOFT}），考虑把细节拆到 references/ 子目录")]
    return []


def check_agents_budget(txt, rel, base=None):
    """常驻提示词预算：总字符 / 行数 / NEVER 密度是「上下文腐烂」的先行指标。

    阈值是**棘轮**：每下沉一批内容到 docs/ 或技能，MUST 把阈值下调至「实际值 + 约 5%」，
    NEVER 只降实际值不降阈值（否则预算永远追着实际上限跑）。业界共识上限约 300 行；
    本文件因沉淀大量失败模式规则高于该线，属可接受偏差，但 MUST 持续下行。
    """
    if rel != "AGENTS.md":
        return []
    lines_list = txt.splitlines()
    chars = len(txt)
    nevers = sum(1 for x in lines_list if "NEVER" in x)
    lines = len(lines_list)
    out = [("OK", f"{rel}  {lines} 行 / {chars} 字符 / NEVER×{nevers}")]
    if chars > BUDGET_CHARS:
        out.append(("WARN", f"{rel}  {chars} 字符（> {BUDGET_CHARS}），考虑继续下沉 §2.2.1 等事实快照"))
    if lines > BUDGET_LINES:
        out.append(("WARN", f"{rel}  {lines} 行（> {BUDGET_LINES}），常驻文件每一行都进每一次会话，继续下沉或合并"))
    if nevers > BUDGET_NEVER_LINES:
        out.append(("WARN", f"{rel}  含 NEVER 的行 {nevers} 条（> {BUDGET_NEVER_LINES}），禁令密度过高会互相打架"))
    return out


# 「由 109 改为 225」这类变更叙述是允许的（属脉络记录），只有当现状引用才算错
RENUMBER_NARRATIVE = re.compile(
    rf"(?:由|从)\s*\*{{0,2}}(?:{'|'.join(STALE_JOB_IDS)})\s*\*{{0,2}}[^，。；\n]{{0,15}}(?:改号为?|改号|变更|挪|让位)"
    rf"|(?:{'|'.join(STALE_JOB_IDS)})\s*(?:改|变为|现为|现分别是)"
)


def check_stale_job_ids(txt, rel, base=None):
    """旧 sys_job 编号残留。

    ERROR  = 把旧编号当**现状**引用（下游会去找不存在的任务）
    WARN   = 「由 X 改为 Y」的**变更叙述**（属脉络记录，按 §5.3 宜收敛到 ADR）
    """
    if not is_constraint(rel):
        return []
    out = []
    for i, line in enumerate(txt.splitlines(), 1):
        if "sys_job" not in line and "job_id" not in line and "JOB_ID" not in line:
            continue
        for jid in STALE_JOB_IDS:
            hit = re.search(rf"(?:sys_job|job_id|JOB_ID)\s*\*{{0,2}}{jid}\b", line) or \
                  re.search(rf"\b{jid}\s*[「\"']", line)
            if not hit:
                continue
            if RENUMBER_NARRATIVE.search(line):
                out.append(("WARN", f"{rel}:{i}  旧编号 {jid} 出现在变更叙述里（宜收敛到 ADR）"))
            else:
                out.append(("ERROR", f"{rel}:{i}  旧任务编号 {jid} 被当现状引用（已改号，NEVER 引用）"))
            break
    return out


def fix_patches(txt, rel):
    """删除「本处此前写 X 已过期」类纯历史补丁片段，返回 (新文本, 删除清单)。"""
    if not is_constraint(rel):
        return txt, []
    removed = []
    # 整段加粗的补丁句
    pat = re.compile(
        r"\*\*[^*]*?(?:本处此前|本文件此前|本条此前|本行此前|本文件那段)[^*]*?"
        r"(?:NEVER 回退|已过期|已作废|是错的)[^*]*?\*\*"
    )

    def _sub(m):
        frag = m.group(0)
        # 保护：§5.3 里描述反模式的原句含占位符「写 X 已过期」，不是真补丁
        if "写 X 已过期" in frag or "反模式" in frag:
            return frag
        removed.append(frag[:70])
        return ""

    new = pat.sub(_sub, txt)
    # 清理删除后残留的孤立标点
    for a, b in [("；；", "；"), ("；，", "，"), ("，；", "；"), ("，。", "。"),
                 ("（；", "（"), ("；）", "）"), ("、；", "、"), ("**；**", ""),
                 ("** **", ""), ("*  *", "*")]:
        new = new.replace(a, b)
    new = re.sub(r"[，；、]\s*([。；])", r"\1", new)
    new = re.sub(r"\*{3,}", "**", new)
    return new, removed


def _load_baseline() -> dict:
    """读存量基线；文件缺失或损坏时返回空基线（所有引用都算新增）。"""
    try:
        data = json.loads(BASELINE.read_text(encoding="utf-8"))
        return data.get("source_line_refs", {}) or {}
    except Exception:
        return {}


def check_source_line_refs(txt, rel, base=None):
    """`Xxx.java:123` 形式的源码行号引用会漂（§5.3 禁令）。

    存量（基线里已有）→ 按文件汇总成**一条** WARN：是债务，不逐条刷屏；
    基线外的新引用 → ERROR：违反「NEVER 新增行号引用」，MUST 按类名/方法名定位。
    清理存量后 MUST `--update-baseline`，否则新增会被基线掩盖成存量。
    """
    if not is_constraint(rel):
        return []
    refs = sorted(set(SOURCE_REF_RE.findall(txt)))
    if not refs:
        return []
    if rel.startswith("skill:"):
        # 技能（第三层）不设存量基线：按需加载、总量小，任何行号引用都算新增
        return [("ERROR", f"{rel}  源码行号引用 {len(refs)} 处"
                          f"（NEVER 新增，MUST 按类名/方法名定位）「{refs[0]}」")]
    known = set(_load_baseline().get(rel, []))
    new = [r for r in refs if r not in known]
    out = []
    if new:
        out.append(("ERROR", f"{rel}  新增源码行号引用 {len(new)} 处"
                             f"（NEVER 新增，MUST 按类名/方法名定位；若为修正存量，清完后 --update-baseline）"
                             f"「{new[0]}」"))
    out.append(("WARN", f"{rel}  源码行号引用存量 {len(refs)} 处"
                        f"（行号会漂，MUST 按类名/方法名定位；例：{refs[0]}）"))
    return out


def update_baseline(targets) -> int:
    """按当前状态重写存量基线，返回收录的引用总数。"""
    data = {"source_line_refs": {}}
    total = 0
    for rel, path in targets:
        if not is_constraint(rel):
            continue
        refs = sorted(set(SOURCE_REF_RE.findall(
            path.read_text(encoding="utf-8", errors="replace"))))
        if refs:
            data["source_line_refs"][rel] = refs
            total += len(refs)
    BASELINE.parent.mkdir(parents=True, exist_ok=True)
    BASELINE.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n",
                        encoding="utf-8")
    return total


# --deep 用的源码索引：路径 → 行数。只索引可能被引用的类型，排除构建产物。
_SRC_INDEX = None


def _source_index() -> dict:
    global _SRC_INDEX
    if _SRC_INDEX is not None:
        return _SRC_INDEX
    t0 = time.time()
    idx = {}
    skip = ("/target/", "/.svn/", "/node_modules/", "/.git/")
    for p in ROOT.rglob("*"):
        s = str(p)
        if any(k in s for k in skip):
            continue
        if p.suffix in (".java", ".xml", ".properties", ".yml"):
            try:
                idx[str(p.relative_to(ROOT))] = p.read_bytes().count(b"\n")
            except OSError:
                pass
    # 前端引用（web/ 下的 .vue/.ts）单独收，避免误报「文件找不到」
    web = ROOT / "web" / "src"
    if web.is_dir():
        for p in web.rglob("*"):
            if p.suffix in (".vue", ".ts"):
                try:
                    idx[str(p.relative_to(ROOT))] = p.read_bytes().count(b"\n")
                except OSError:
                    pass
    _SRC_INDEX = idx
    print(f"（--deep 源码索引：{len(idx)} 个文件，{time.time()-t0:.1f}s）")
    return idx


def _resolve_ref(rp: str):
    """把文档里的源码路径解析到 (行数, 状态)。

    文档常把路径缩写成 `模块/.../包/类.java`（`...` 是省略号不是路径段），
    也可能只写文件名。按「全路径 → 去掉 ... 的尾部 → 裸文件名」逐级尝试；
    同名多文件无法唯一解析时保守跳过（判 ambiguous，绝不算错）。
    """
    idx = _source_index()
    if rp in idx:
        return idx[rp], "resolved"
    cands = []
    tail = rp.split(".../")[-1].lstrip("/")
    if tail != rp:
        cands.append(tail)
    cands.append(Path(rp).name)
    for c in cands:
        hits = {v for k, v in idx.items() if k.endswith("/" + c) or k == c}
        if len(hits) == 1:
            return hits.pop(), "resolved"
        if len(hits) > 1:
            return None, "ambiguous"
    return None, "missing"


def deep_source_refs(targets):
    """把每条源码行号引用与源码树核对，按 md 文件汇总三类（诊断，不影响退出码）。

    文件找不到 = 类已删/已改名（最危险，如随拆分消失的宿主类）；
    行号越界   = 文件在、行号漂了（弱信号）；
    同名多文件 = 引用路径被缩写，无法唯一解析（跳过，不算错）。
    """
    rows = []
    for rel, path in targets:
        if not is_constraint(rel):
            continue
        txt = path.read_text(encoding="utf-8", errors="replace")
        refs = sorted(set(SOURCE_REF_RE.findall(txt)))
        if not refs:
            continue
        missing, oob, amb = [], [], []
        for ref in refs:
            rp, _, ln = ref.rpartition(":")
            try:
                n = int(ln)
            except ValueError:
                continue
            total, status = _resolve_ref(rp)
            if status == "missing":
                missing.append(ref)
            elif status == "ambiguous":
                amb.append(ref)
            elif n > total:
                oob.append(ref)
        if missing or oob:
            rows.append((rel, missing, oob, amb))
    print()
    print("--deep 源码行号引用核对（存量债务诊断，不进门禁）：")
    if not rows:
        print("  全部引用均可解析且行号在范围内。")
    for rel, missing, oob, amb in rows:
        parts = []
        if missing:
            parts.append(f"文件找不到 {len(missing)}（类已删/改名）：{missing[0]}")
        if oob:
            parts.append(f"行号越界 {len(oob)}：{oob[0]}")
        if amb:
            parts.append(f"同名多文件跳过 {len(amb)}")
        print(f"  {rel}  " + "；".join(parts))
    return rows


# 有意记载的已删除文件（文档里写「XXX 已删除 NEVER 重建」属正确记述，不是断链）
DELETED_ON_PURPOSE = {"docs/README.md", "docs/API.md"}


def check_broken_links(txt, rel, base=None):
    """引用了仓库里不存在的文件路径 —— 必然误导后续改动。

    `../x.md` 按**文件所在目录**解析；其余按仓库根解析。
    """
    if not is_constraint(rel):
        return []
    base_dir = base if base is not None else ROOT
    out = []
    # 只查 .md 间引用：sql/sh 的路径在文档里常按模块目录简写，会误报
    pat = re.compile(r"`((?:\.\./|[\w.-]+/)+[\w.-]+\.md)`")
    for m in pat.finditer(txt):
        ref = m.group(1)
        if ref in DELETED_ON_PURPOSE:
            continue
        cand = (base_dir / ref).resolve() if ref.startswith(("../", "./")) else (ROOT / ref)
        if not cand.exists():
            out.append(("ERROR", f"{rel}  断链：`{ref}` 不存在"))
    return out


def check_line_length(txt, rel, base=None, limit=2000):
    """单行超长会稀释注意力（模型对超长行中段的遵从度明显下降）。"""
    if not is_constraint(rel):
        return []
    return [
        ("WARN", f"{rel}:{i}  单行 {len(line)} 字符（> {limit}）")
        for i, line in enumerate(txt.splitlines(), 1)
        if len(line) > limit
    ]


CHECKS = [
    check_contract_present,
    check_no_patches,
    check_agents_budget,
    check_stale_job_ids,
    check_broken_links,
    check_source_line_refs,
    check_line_length,
]

# 第三层（技能）复用的检查子集：不查核心约定与常驻预算（那是 AGENTS.md 专属），
# 断链检查对技能同样有效（技能里的 `docs/**.md` 引用按仓库根解析）。
SKILL_CHECKS = [
    check_no_patches,
    check_stale_job_ids,
    check_broken_links,
    check_source_line_refs,
    check_line_length,
    check_skill_budget,
]


def main():
    do_fix = "--fix" in sys.argv
    if not AGENTS.exists():
        print(f"ERROR  找不到 {AGENTS}")
        return 1

    results = {"ERROR": [], "WARN": [], "OK": []}
    targets = [("AGENTS.md", AGENTS)] + [
        (str(p.relative_to(ROOT)), p) for p in sorted((ROOT / "docs").rglob("*.md"))
    ]
    if "--update-baseline" in sys.argv:
        total = update_baseline(targets)
        print(f"已重写存量基线 {BASELINE.relative_to(ROOT)}：收录 {total} 处源码行号引用"
              f"（{len(_load_baseline())} 个文件）。")
    fixed_total = 0
    for rel, path in targets:
        txt = path.read_text(encoding="utf-8", errors="replace")
        if do_fix:
            txt, removed = fix_patches(txt, rel)
            for r in removed:
                print(f"FIXED  {rel}  删除补丁句「{r}」")
                fixed_total += 1
            if removed:
                path.write_text(txt, encoding="utf-8")
        for fn in CHECKS:
            for level, msg in fn(txt, rel, path.parent):
                results[level].append(msg)

    # 第三层：技能（仓库外，与约束层同一套腐烂规则；技能不设存量基线）
    skill_n = 0
    if "--no-skills" not in sys.argv and SKILLS_ROOT.is_dir():
        for p in sorted(SKILLS_ROOT.glob("qditp-*/SKILL.md")):
            rel = f"skill:{p.parent.name}"
            txt = p.read_text(encoding="utf-8", errors="replace")
            skill_n += 1
            for fn in SKILL_CHECKS:
                for level, msg in fn(txt, rel, p.parent):
                    results[level].append(msg)

    verbose = "-v" in sys.argv or "--verbose" in sys.argv
    # ERROR 全打；WARN 默认只打汇总 + 每类 3 条样本（否则 4000+ 条没人看）
    for msg in results["ERROR"]:
        print(f"{'ERROR':<6} {msg}")

    import collections

    def _kind(msg):
        k = msg.split("  ", 2)[-1]
        k = k.split("（")[0].split("「")[0].strip()
        return re.sub(r"\d+", "N", k)  # 数字归一，让「存量 N 处」按类聚合

    warn_kind = collections.Counter()
    for msg in results["WARN"]:
        warn_kind[_kind(msg)] += 1
    print()
    print("WARN 汇总（按类型）：")
    for kind, n in warn_kind.most_common():
        print(f"  {n:>5}  {kind}")
        if not verbose:
            shown = [m for m in results["WARN"] if _kind(m) == kind][:3]
            for m in shown:
                print(f"        例：{m}")
    if verbose:
        print()
        for msg in results["WARN"]:
            print(f"{'WARN':<6} {msg}")

    if "--deep" in sys.argv:
        deep_source_refs(targets)

    n_checked = sum(1 for rel, _ in targets if is_constraint(rel))
    extra = f" + {skill_n} 个技能" if skill_n else ""
    print()
    print(f"扫描 {len(targets)} 篇 md（约束层 {n_checked} 篇）{extra}："
          f"ERROR {len(results['ERROR'])} / WARN {len(results['WARN'])} / OK {len(results['OK'])}")
    if do_fix:
        print(f"\n--fix 共删除 {fixed_total} 处补丁句。")
    if results["ERROR"]:
        print("ERROR 项违反 AGENTS.md 核心约定，MUST 修完再提交。")
        return 1
    print("无 ERROR。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
