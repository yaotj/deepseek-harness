#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""qd-itp 提示词健康检查（lint）

把 `AGENTS.md` 的工程纪律变成可机械执行的检查。
核心思想：**能用代码保证的，就不要写在提示词里指望模型记住。**

用法：
    python3 docs/提示词/lint-prompts.py [仓库根] [-v] [--fix] [--update-baseline] [--deep] [--no-skills]

  -v           打印全部 WARN（默认只打汇总 + 每类 3 条样本）
  --fix        自动删除「本处此前写 X 已过期」类纯历史补丁句
  --update-baseline  重新生成源码行号引用基线
  --deep       额外把每条源码行号引用与源码树核对（约 13 秒；诊断用）
  --no-skills  跳过第三层技能检查
  --show-sections  打印各文件真实章节号（§引用机检的判定依据）

**覆盖提示词体系三层**：① 常驻层 AGENTS.md（常驻预算）；
② 按需层 docs/ 约束层文档（断链 / 补丁句 / 旧编号 / 行号引用基线 / 跨文件 § 引用）；
③ 技能层 qditp-*（同一套腐烂规则，不设存量基线）。

分级：ERROR（退出码 1）= 必然误导后续改动；WARN（退出码 0）= 需要人看一眼。

**只查「约束层」文档**（能指挥后续改动）；
`docs/testing/`、`docs/reviews/`、`docs/history/`、甲方规格原文是记录与归档，不在检查范围。

新增检查项 MUST 在 CHECKS 里加一个函数，并在 docstring 写清判据。
"""

import json, re, sys, time, collections
from pathlib import Path

_args = [a for a in sys.argv[1:] if not a.startswith("-")]
ROOT = Path(_args[0] if _args else ".").resolve()
AGENTS = ROOT / "AGENTS.md"
BASELINE = ROOT / "docs" / "提示词" / "lint-baseline.json"

SOURCE_REF_RE = re.compile(r"[\w./-]+\.(?:java|xml|properties|yml|vue|ts):\d+")
STALE_JOB_IDS = ["133","134","135","265","235","109","105","122","123"]
PATCH_RE = re.compile(r"(本处此前|本文件此前|本条此前|本行此前)[^。；\n]{0,80}(已过期|已作废|NEVER 回退|是错的)")
CONSTRAINT_PREFIXES = ("docs/business/","docs/domain/","docs/ops/","docs/architecture/","docs/提示词/")

def is_constraint(rel):
    return rel == "AGENTS.md" or rel.startswith("skill:") or any(rel.startswith(p) for p in CONSTRAINT_PREFIXES)

BUDGET_CHARS, BUDGET_LINES, BUDGET_NEVER_LINES = 37000, 380, 80
SKILL_LINES_SOFT = 350
SKILLS_ROOT = Path.home() / ".workbuddy" / "skills"

RENUMBER_NARRATIVE = re.compile(
    rf"(?:由|从)\s*\*{{0,2}}(?:{'|'.join(STALE_JOB_IDS)})\s*\*{{0,2}}[^，。；\n]{{0,15}}(?:改号为?|改号|变更|挪|让位)"
    rf"|(?:{'|'.join(STALE_JOB_IDS)})\s*(?:改|变为|现为|现分别是)"
)

# 有意删除 / 有意迁走的文件：引用它不算断链（docs/README.md 已删；
# docs/ops/workflow-routes.md 于 2026-10-20 迁至 docs/提示词/任务路由表.md；
# docs/ops/定时任务清单.csv 与 docs/ops/定时任务需求对照.md 于 2026-10-20 合并为 docs/ops/定时任务总览.md，
# ADR 正文（decisions.md）与总览 §五「合并自」说明里的旧文件名引用不算断链）
_DELETED_ON_PURPOSE = {
    "docs/README.md", "docs/API.md", "docs/ops/workflow-routes.md",
    "docs/reviews/提示词过期检查记录-2026-09-23.md",
    "docs/ops/定时任务清单.csv", "docs/ops/定时任务需求对照.md",
}
_SEC_HDR = re.compile(r"^#{2,3}\s+(\d+)(?:\.(\d+))?(?:[、．.\s]|$)", re.M)
_CROSS_SEC = re.compile(r"(?<![\w/])((?:AGENTS|SKILL|docs/[\u4e00-\u9fa5\w\-./]+)\.md)\s*§(\d+)(?:\.(\d+))?(?![\d.])")
_SRC_INDEX = None


def _load_baseline():
    try:
        return json.loads(BASELINE.read_text(encoding="utf-8")).get("source_line_refs", {}) or {}
    except Exception:
        return {}


def check_no_patches(txt, rel, base=None):
    """§4.3：事实过期 MUST 直接改旧值，NEVER 追加补丁句。"""
    if not is_constraint(rel): return []
    hits = []
    for i, line in enumerate(txt.splitlines(), 1):
        for m in PATCH_RE.finditer(line):
            frag = line[max(0,m.start()-40):m.end()+40]
            if "写 X 已过期" in frag or "反模式" in frag: continue
            hits.append(("ERROR", f"{rel}:{i}  补丁句「{m.group(0)[:60]}」"))
    return hits


def check_agents_budget(txt, rel, base=None):
    """常驻提示词预算：总字符 / 行数 / NEVER 密度是上下文腐烂先行指标。"""
    if rel != "AGENTS.md": return []
    lines_list = txt.splitlines()
    chars, nevers, lines = len(txt), sum(1 for x in lines_list if "NEVER" in x), len(lines_list)
    out = [("OK", f"{rel}  {lines} 行 / {chars} 字符 / NEVER×{nevers}")]
    if chars > BUDGET_CHARS: out.append(("WARN", f"{rel}  {chars} 字符（> {BUDGET_CHARS}）"))
    if lines > BUDGET_LINES: out.append(("WARN", f"{rel}  {lines} 行（> {BUDGET_LINES}），继续下沉或合并"))
    if nevers > BUDGET_NEVER_LINES: out.append(("WARN", f"{rel}  NEVER 行 {nevers} 条（> {BUDGET_NEVER_LINES}），禁令密度过高"))
    return out


def check_skill_budget(txt, rel, base=None):
    """技能单篇长度：超长宜把细节拆到 references/。"""
    if not rel.startswith("skill:"): return []
    lines = txt.count("\n") + 1
    if lines > SKILL_LINES_SOFT:
        return [("WARN", f"{rel}  {lines} 行（> {SKILL_LINES_SOFT}），考虑拆到 references/ 子目录")]
    return []


def check_stale_job_ids(txt, rel, base=None):
    """旧 sys_job 编号残留。"""
    if not is_constraint(rel): return []
    out = []
    for i, line in enumerate(txt.splitlines(), 1):
        if "sys_job" not in line and "job_id" not in line and "JOB_ID" not in line: continue
        for jid in STALE_JOB_IDS:
            hit = re.search(rf"(?:sys_job|job_id|JOB_ID)\s*\*{{0,2}}{jid}\b", line) or re.search(rf"\b{jid}\s*[「\"']", line)
            if not hit: continue
            if RENUMBER_NARRATIVE.search(line):
                out.append(("WARN", f"{rel}:{i}  旧编号 {jid} 出现在变更叙述里（宜收敛到 ADR）"))
            else:
                out.append(("ERROR", f"{rel}:{i}  旧任务编号 {jid} 被当现状引用（已改号，NEVER 引用）"))
            break
    return out


def check_broken_links(txt, rel, base=None):
    """引用了仓库里不存在的 .md 文件路径。"""
    if not is_constraint(rel): return []
    base_dir = base if base is not None else ROOT
    out = []
    pat = re.compile(r"`((?:\.\./|[\w.-]+/)+[\w.-]+\.md)`")
    for m in pat.finditer(txt):
        ref = m.group(1)
        if ref in _DELETED_ON_PURPOSE: continue
        # 相对路径（`../`、`./` 开头）按文档所在目录解析；
        # 绝对式（`docs/` / `scripts/` 等仓库根级别路径）直接按根解析，避免 base_dir 拼出 `docs/ops/docs/ops/...` 假断链。
        if ref.startswith(("../","./")):
            cand = (base_dir / ref).resolve()
        elif ref.startswith(("docs/","scripts/")):
            cand = (ROOT / ref).resolve()
        else:
            cand = (base_dir / ref).resolve()
        if not cand.exists():
            out.append(("ERROR", f"{rel}  断链：`{ref}` 不存在"))
    return out


def check_source_line_refs(txt, rel, base=None):
    """源码行号引用会漂（§4.3 禁令）。"""
    if not is_constraint(rel): return []
    refs = sorted(set(SOURCE_REF_RE.findall(txt)))
    if not refs: return []
    if rel.startswith("skill:"):
        return [("ERROR", f"{rel}  源码行号引用 {len(refs)} 处（NEVER 新增，MUST 按类名/方法名定位）「{refs[0]}」")]
    known = set(_load_baseline().get(rel, []))
    new = [r for r in refs if r not in known]
    out = []
    if new:
        out.append(("ERROR", f"{rel}  新增源码行号引用 {len(new)} 处（NEVER 新增）「{new[0]}」"))
    out.append(("WARN", f"{rel}  源码行号引用存量 {len(refs)} 处（行号会漂，MUST 按类名/方法名定位；例：{refs[0]}）"))
    return out


def check_line_length(txt, rel, base=None, limit=2000):
    """单行超长会稀释注意力。"""
    if not is_constraint(rel): return []
    return [("WARN", f"{rel}:{i}  单行 {len(line)} 字符（> {limit}）")
            for i, line in enumerate(txt.splitlines(), 1) if len(line) > limit]


def fix_patches(txt, rel):
    """删除补丁句，返回 (新文本, 删除清单)。"""
    if not is_constraint(rel): return txt, []
    removed = []
    pat = re.compile(r"\*\*[^*]*?(?:本处此前|本文件此前|本条此前|本行此前|本文件那段)[^*]*?(?:NEVER 回退|已过期|已作废|是错的)[^*]*?\*\*")
    def _sub(m):
        frag = m.group(0)
        if "写 X 已过期" in frag or "反模式" in frag: return frag
        removed.append(frag[:70]); return ""
    new = pat.sub(_sub, txt)
    for a, b in [("；；","；"),("；，","，"),("，；","；"),("，。","。"),("（；","（"),("；）","）"),("、；","、"),("**；**",""),("** **",""),("*  *","*")]:
        new = new.replace(a, b)
    new = re.sub(r"[，；、]\s*([。；])", r"\1", new)
    new = re.sub(r"\*{3,}", "**", new)
    return new, removed


def update_baseline(targets):
    """重写存量基线，返回收录的引用总数。"""
    data = {"source_line_refs": {}}
    total = 0
    for rel, path in targets:
        if not is_constraint(rel): continue
        refs = sorted(set(SOURCE_REF_RE.findall(path.read_text(encoding="utf-8", errors="replace"))))
        if refs: data["source_line_refs"][rel] = refs; total += len(refs)
    BASELINE.parent.mkdir(parents=True, exist_ok=True)
    BASELINE.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return total


def build_section_index(targets):
    """文件 rel → 真实存在的章节号集合。"""
    idx = {}
    for rel, path in targets:
        try: txt = path.read_text(encoding="utf-8", errors="replace")
        except OSError: continue
        secs = set()
        for m in _SEC_HDR.finditer(txt):
            top, sub = m.group(1), m.group(2)
            secs.add(top)
            if sub: secs.add(f"{top}.{sub}")
        if secs: idx[rel] = secs
    return idx


def check_section_refs(targets, section_index):
    """跨文件「文件名 §N」引用指向的章节是否存在（§4.3 悬空章节引用的机检）。"""
    problems = []
    for rel, path in targets:
        if rel.startswith("skill:"): continue
        try: txt = path.read_text(encoding="utf-8", errors="replace")
        except OSError: continue
        counts = {}
        for m in _CROSS_SEC.finditer(txt):
            tgt = m.group(1)
            sec = (m.group(2) + "." + m.group(3)) if m.group(3) else m.group(2)
            counts[(tgt, sec)] = counts.get((tgt, sec), 0) + 1
        for (tgt, sec), n in sorted(counts.items()):
            if tgt in section_index and sec not in section_index[tgt]:
                problems.append((rel, tgt, sec, n))
    out = []
    for rel, tgt, sec, n in problems:
        out.append(("WARN", f"{rel}  引用 `{tgt} §{sec}` 但目标文件无此章节（{n} 处）——章节已重排/删改，MUST 改指存在的章节或删除该引用"))
    return out


# ── 2026-10-20 新增：路由表单一真源 + § 引用语义锚点 ────────────────────────
# 背景：① 原「唯一真源」路由表与 AGENTS.md §5.1 是两份表，靠人同步；
#       ② § 引用只验「章节号存在」，章节重排 / 规则下沉后语义漂移无人拦截。
ROUTE_TABLE_REL = "docs/提示词/任务路由表.md"
_FREQ_MARK = "★"
_MD_PATH_RE = re.compile(r"(?:docs|scripts)/[^\s`（）()，、；,;｜|]+?\.md")
_HEAD_RE = re.compile(r"^(#{2,6})\s+(.*)$")
_SECNUM_RE = re.compile(r"^(\d+(?:\.\d+)*|[一二三四五六七八九十]+)\s*[、．.、]?\s*(.*)$")
_REF_RE = re.compile(
    r"(?<![\w/])((?:AGENTS|SKILL|docs/[\u4e00-\u9fa5\w\-./]+)\.md)`?\s*§\s*"
    r"(\d+(?:\.\d+)*|[一二三四五六七八九十]+)(?![0-9.])"
)
_ANCHOR_STOP = set("、，,。;；|）)）`：:\n")
_ANCHOR_OPEN = {"「": "」", "『": "』", "《": "》", "（": "）", "(": "）"}
# § 编号与锚点之间允许的连接字（避免把「为「...」」里的「为」当成锚点开头）
_CONNECT = set("为即中的是有与在按据且则当从以")


def _norm(s):
    return re.sub(r"\s+", "", (s or "").replace("**", "").replace("`", "")).lower()


def _md_paths(cell):
    """抽取单元格里的仓库内 .md 路径；占位符（`docs/business/<域>.md`）不计入。"""
    out = set()
    for p in _MD_PATH_RE.findall(cell or ""):
        if "<" in p or ">" in p:
            continue
        out.add(p)
    return out


def _table_rows(txt):
    """解析 markdown 表格行 → [[cell0, cell1, ...], ...]（跳过表头与分隔行）。"""
    rows = []
    for line in (txt or "").splitlines():
        s = line.strip()
        if not s.startswith("|"):
            continue
        cells = [c.strip() for c in s.strip("|").split("|")]
        if len(cells) < 2:
            continue
        head = cells[0].replace(" ", "")
        if head == "任务类型" or not head or set(head) <= set("-:"):
            continue
        rows.append(cells)
    return rows


def _section_body(txt, head_pat):
    """取出 AGENTS.md 中标题匹配 head_pat 的章节正文（到下一个同级或更高级标题为止）。"""
    lines = (txt or "").splitlines()
    start, level = None, 0
    for i, ln in enumerate(lines):
        m = _HEAD_RE.match(ln)
        if m and re.match(head_pat, ln.strip()):
            start, level = i + 1, len(m.group(1))
            break
    if start is None:
        return ""
    out = []
    for ln in lines[start:]:
        m = _HEAD_RE.match(ln)
        if m and len(m.group(1)) <= level:
            break
        out.append(ln)
    return "\n".join(out)


def check_route_sync(targets):
    """路由表（唯一真源）的 ★ 高频集合 MUST 与 AGENTS.md §5.1 投影完全一致。"""
    rt = ROOT / ROUTE_TABLE_REL
    agents = ROOT / "AGENTS.md"
    if not rt.exists():
        return [("ERROR", f"{ROUTE_TABLE_REL}  不存在——它是「任务→读哪篇」的唯一真源，MUST 保留")]
    freq = set()
    for cells in _table_rows(rt.read_text(encoding="utf-8", errors="replace")):
        if cells[0].startswith(_FREQ_MARK):
            freq |= _md_paths(cells[1])
    sec = _section_body(agents.read_text(encoding="utf-8", errors="replace"), r"^#{2,6}\s+5\.1(?!\d)")
    proj = set()
    for cells in _table_rows(sec):
        proj |= _md_paths(cells[1])
    if not proj:
        return [("ERROR", "AGENTS.md §5.1  未解析到路由表行——MUST 保持「任务类型 | 先读 | 再读」三列表格，否则本检查失效")]
    out = []
    only_rt = sorted(freq - proj)
    only_ag = sorted(proj - freq)
    if only_rt:
        out.append(("ERROR", f"AGENTS.md §5.1  缺路由表 ★ 行的先读路径 {len(only_rt)} 处（MUST 同步投影）：{only_rt[0]}"))
    if only_ag:
        out.append(("ERROR", f"AGENTS.md §5.1  多出路由表 ★ 集合外的先读路径 {len(only_ag)} 处（MUST 回改路由表或删本行）：{only_ag[0]}"))
    if not only_rt and not only_ag:
        out.append(("OK", f"路由表 ★ 高频 {len(freq)} 个路径与 AGENTS.md §5.1 投影一致"))
    return out


def _build_sections(txt):
    """{章节号: (标题, 正文)}；章节号支持 4 / 4.1 / 四 三种写法。

    **无编号的子标题（`### 规则` / `### 现状`）不切分父节**，其正文归入父节——
    否则 `runtime-observability-traps.md` §三 的正文会被截成空串，规则级锚点永远匹配不上。
    """
    secs, cur, head, body = {}, None, "", []
    for ln in (txt or "").splitlines():
        m = _HEAD_RE.match(ln)
        if m:
            title = m.group(2).strip()
            nm = _SECNUM_RE.match(title)
            if nm:
                if cur is not None:
                    secs[cur] = (head, "\n".join(body))
                cur, head, body = nm.group(1), nm.group(2).strip(), []
            elif cur is not None:
                body.append(ln)          # 无编号子标题：正文归入当前编号节
        elif cur is not None:
            body.append(ln)
    if cur is not None:
        secs[cur] = (head, "\n".join(body))
    return secs


def _anchor_of(rest):
    """抽 § 编号之后的锚点。

    返回 (kind, anchor)：
      'braced' → 紧随其后（可隔 1~3 个连接字）的引号 / 括号 / 反引号内容，是明确的语义锚点；
      'plain'  → 紧随其后的短语，MUST 由调用方确认它就是目标章节标题；
      'none'   → 没有锚点。
    """
    rest = (rest or "").lstrip()
    if not rest:
        return ("none", "")
    i = 0
    while i < len(rest) and i < 3 and rest[i] in _CONNECT:
        i += 1
    tail = rest[i:]
    if not tail:
        return ("none", "")
    if tail[0] in _ANCHOR_OPEN:
        close = _ANCHOR_OPEN[tail[0]]
        end = tail.find(close, 1)
        if end > 0:
            return ("braced", tail[1:end].strip())
    if tail[0] == "`":
        end = tail.find("`", 1)
        if end > 0:
            return ("braced", tail[1:end].strip())
    buf = []
    for ch in tail:
        if ch in _ANCHOR_STOP:
            break
        buf.append(ch)
    plain = "".join(buf).strip()
    return ("plain", plain) if plain else ("none", "")


def _bigrams(s):
    s = _norm(s)
    return {s[i:i + 2] for i in range(len(s) - 1)}


def _anchor_match(anchor, text):
    """锚点与目标章节的字符二元组重叠率 >= 0.5 ⇒ 指的是该章节里的这条规则。

    用重叠率而非精确子串：引用常把规则名简写（「NEVER 新建工具类」vs 原文
    「NEVER 主动创建新的工具类」），精确匹配会把这类合法引用误判成过期。
    """
    a = _bigrams(anchor)
    if not a:
        return False
    b = _bigrams(text)
    if not b:
        return False
    return len(a & b) / len(a) >= 0.5


def check_section_anchors(targets):
    """跨文件 § 引用 MUST 带锚点；锚点 MUST 能在目标章节正文中找到（章节重排 / 规则下沉后语义漂移即拦截）。

    分级：
      ERROR —— 带了锚点、但锚点与目标章节几乎无重叠 = 引用指着已搬走 / 已改写的内容（必然误导）；
      WARN  —— 裸引用（无锚点）= 无法机检，章节号一重排就静默漂移，MUST 补锚点。
    只查约束层文档（docs/testing/、docs/reviews/、docs/history/ 是归档记录，不拦门禁）。
    """
    out, cache = [], {}
    for rel, path in targets:
        if not is_constraint(rel):
            continue
        try:
            txt = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in _REF_RE.finditer(txt):
            tgt, sec = m.group(1), m.group(2)
            if tgt not in cache:
                tpath = ROOT / tgt
                cache[tgt] = _build_sections(tpath.read_text(encoding="utf-8", errors="replace")) if tpath.exists() else None
            tgt_secs = cache[tgt]
            if not tgt_secs or sec not in tgt_secs:
                continue                      # 章节号不存在 → 交给 check_section_refs
            kind, anchor = _anchor_of(txt[m.end():])
            # 「§号是否落在反引号代码段内」用**反引号奇偶性**判定：数到 §号结束时的反引号个数，
            # 奇数 = 落在代码段内（无锚点可取，跳过）；偶数 = 在代码段外（正常取锚点）。
            # 不能用「§号后紧跟反引号」判定——后面跟的可能是另一个术语的反引号（如 `VLOGS_URL` 行）。
            if txt[:m.end()].count("`") % 2 == 1:
                kind, anchor = "none", ""
            if kind == "none" or (kind == "plain" and "/" in anchor):
                if kind == "none" and tgt == "AGENTS.md":
                    out.append(("WARN", f"{rel}  引用 `AGENTS.md §{sec}` 无锚点，无法机检——MUST 补成「AGENTS.md §{sec}「<章节标题>」」"))
                continue                      # §二/§三/§六 这类章节列表
            head, body = tgt_secs[sec]
            if kind == "braced":
                ok = _anchor_match(anchor, head + "\n" + body)
            else:
                ok = bool(_norm(anchor)) and _norm(head).startswith(_norm(anchor))
            if ok:
                continue
            if kind == "braced":
                out.append(("ERROR", f"{rel}  引用 `{tgt} §{sec}「{anchor[:24]}」` 的锚点在目标章节中不存在——规则已下沉 / 章节已重排，MUST 改指真实所在章节"))
            else:
                out.append(("WARN", f"{rel}  引用 `{tgt} §{sec}` 的短语「{anchor[:20]}」不是目标章节标题，无法机检——MUST 改写成带锚点的形式"))
    return out


def _source_index():
    global _SRC_INDEX
    if _SRC_INDEX is not None: return _SRC_INDEX
    t0 = time.time(); idx = {}
    skip = ("/target/","/.svn/","/node_modules/","/.git/")
    for p in ROOT.rglob("*"):
        s = str(p)
        if any(k in s for k in skip): continue
        if p.suffix in (".java",".xml",".properties",".yml"):
            try: idx[str(p.relative_to(ROOT))] = p.read_bytes().count(b"\n")
            except OSError: pass
    web = ROOT / "web" / "src"
    if web.is_dir():
        for p in web.rglob("*"):
            if p.suffix in (".vue",".ts"):
                try: idx[str(p.relative_to(ROOT))] = p.read_bytes().count(b"\n")
                except OSError: pass
    _SRC_INDEX = idx
    print(f"（--deep 源码索引：{len(idx)} 个文件，{time.time()-t0:.1f}s）")
    return idx


def _resolve_ref(rp):
    idx = _source_index()
    if rp in idx: return idx[rp], "resolved"
    cands = []
    tail = rp.split(".../")[-1].lstrip("/")
    if tail != rp: cands.append(tail)
    cands.append(Path(rp).name)
    for c in cands:
        hits = {v for k, v in idx.items() if k.endswith("/" + c) or k == c}
        if len(hits) == 1: return hits.pop(), "resolved"
        if len(hits) > 1: return None, "ambiguous"
    return None, "missing"


def deep_source_refs(targets):
    """把每条源码行号引用与源码树核对（诊断，不影响退出码）。"""
    rows = []
    for rel, path in targets:
        if not is_constraint(rel): continue
        txt = path.read_text(encoding="utf-8", errors="replace")
        refs = sorted(set(SOURCE_REF_RE.findall(txt)))
        if not refs: continue
        missing, oob, amb = [], [], []
        for ref in refs:
            rp, _, ln = ref.rpartition(":")
            try: n = int(ln)
            except ValueError: continue
            total, status = _resolve_ref(rp)
            if status == "missing": missing.append(ref)
            elif status == "ambiguous": amb.append(ref)
            elif n > total: oob.append(ref)
        if missing or oob: rows.append((rel, missing, oob, amb))
    print(); print("--deep 源码行号引用核对（存量债务诊断，不进门禁）：")
    if not rows: print("  全部引用均可解析且行号在范围内。")
    for rel, missing, oob, amb in rows:
        parts = []
        if missing: parts.append(f"文件找不到 {len(missing)}（类已删/改名）：{missing[0]}")
        if oob: parts.append(f"行号越界 {len(oob)}：{oob[0]}")
        if amb: parts.append(f"同名多文件跳过 {len(amb)}")
        print(f"  {rel}  " + "；".join(parts))
    return rows


CHECKS = [
    check_no_patches,
    check_agents_budget,
    check_stale_job_ids,
    check_broken_links,
    check_source_line_refs,
    check_line_length,
]

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
        print(f"ERROR  找不到 {AGENTS}"); return 1

    results = {"ERROR": [], "WARN": [], "OK": []}
    targets = [("AGENTS.md", AGENTS)] + [(str(p.relative_to(ROOT)), p) for p in sorted((ROOT / "docs").rglob("*.md"))]
    if "--update-baseline" in sys.argv:
        total = update_baseline(targets)
        print(f"已重写存量基线 {BASELINE.relative_to(ROOT)}：收录 {total} 处源码行号引用（{len(_load_baseline())} 个文件）。")
    fixed_total = 0
    for rel, path in targets:
        txt = path.read_text(encoding="utf-8", errors="replace")
        if do_fix:
            txt, removed = fix_patches(txt, rel)
            for r in removed:
                print(f"FIXED  {rel}  删除补丁句「{r}」"); fixed_total += 1
            if removed: path.write_text(txt, encoding="utf-8")
        for fn in CHECKS:
            for level, msg in fn(txt, rel, path.parent):
                results[level].append(msg)

    skill_n = 0
    if "--no-skills" not in sys.argv and SKILLS_ROOT.is_dir():
        for p in sorted(SKILLS_ROOT.glob("qditp-*/SKILL.md")):
            rel = f"skill:{p.parent.name}"
            txt = p.read_text(encoding="utf-8", errors="replace")
            skill_n += 1
            for fn in SKILL_CHECKS:
                for level, msg in fn(txt, rel, p.parent):
                    results[level].append(msg)

    # ── 全部检查 MUST 在输出前跑完 ──
    # 历史缺陷：§ 引用检查曾跑在输出之后，结果既不打印也不影响退出码，等于从未生效。
    section_index = build_section_index(targets)
    for level, msg in check_section_refs(targets, section_index): results[level].append(msg)
    for level, msg in check_route_sync(targets): results[level].append(msg)
    for level, msg in check_section_anchors(targets): results[level].append(msg)

    verbose = "-v" in sys.argv or "--verbose" in sys.argv
    for msg in results["ERROR"]: print(f"{'ERROR':<6} {msg}")

    def _kind(msg):
        k = msg.split("  ", 2)[-1]; k = k.split("（")[0].split("「")[0].strip()
        return re.sub(r"\d+", "N", k)

    warn_kind = collections.Counter()
    for msg in results["WARN"]: warn_kind[_kind(msg)] += 1
    print(); print("WARN 汇总（按类型）：")
    for kind, n in warn_kind.most_common():
        print(f"  {n:>5}  {kind}")
        if not verbose:
            shown = [m for m in results["WARN"] if _kind(m) == kind][:3]
            for m in shown: print(f"        例：{m}")
    if verbose:
        print(); [print(f"{'WARN':<6} {msg}") for msg in results["WARN"]]

    if "--deep" in sys.argv: deep_source_refs(targets)

    if "--show-sections" in sys.argv:
        print(); print("各文件真实章节号（§引用机检的判定依据，供人工核对）：")
        for rel, secs in sorted(section_index.items()): print(f"  {rel}: {secs}")

    n_checked = sum(1 for rel, _ in targets if is_constraint(rel))
    extra = f" + {skill_n} 个技能" if skill_n else ""
    print(f"\n扫描 {len(targets)} 篇 md（约束层 {n_checked} 篇）{extra}：ERROR {len(results['ERROR'])} / WARN {len(results['WARN'])} / OK {len(results['OK'])}")
    if do_fix: print(f"\n--fix 共删除 {fixed_total} 处补丁句。")
    if results["ERROR"]:
        print("ERROR 项违反 AGENTS.md 核心约定，MUST 修完再提交。"); return 1
    print("无 ERROR。"); return 0


if __name__ == "__main__":
    sys.exit(main())
