#!/usr/bin/env python3
"""分域 schema 生成器：把仓库内 46 个 DDL 源文件按 4 域（PLAT/USER/RIDE/TXN）拆分，
生成可执行的建表脚本 + 清单。表清单以 20261014_bundle_m2_m3_migration.sql 的 IN 列表为
权威，与 4 个 export 脚本的 tables= 交叉校验。"""
import re, os, json
from collections import defaultdict

ROOT = "/Users/tuanjie/workspace/company/chinasofti/qd/qditp"
MIG = os.path.join(ROOT, "scripts/migration")

# ---------- 1. 域清单（从 bundle 解析，权威源；只取 IN (...) 子句，避免混入 UTL_FILE 等字符串） ----------
bundle = open(os.path.join(MIG, "20261014_bundle_m2_m3_migration.sql"), encoding="utf-8").read()
domains = {}
for m in re.finditer(r"v_tgt\s+VARCHAR2\(30\)\s*:=\s*'(QDITP_\w+)'", bundle):
    tgt = m.group(1)
    # 取该域声明之后最近的 WHERE table_name IN (...) 子句
    tail = bundle[m.end():]
    inm = re.search(r"WHERE\s+table_name\s+IN\s*\((.*?)\)", tail, re.S)
    if inm:
        names = re.findall(r"'([A-Z0-9_]+)'", inm.group(1))
        if names:
            domains[tgt] = sorted(set(names))

# export 脚本交叉校验
export_lists = {}
for dom, fn in [("QDITP_PLAT", "20261014_export_qditp_plat.sql"),
                ("QDITP_USER", "20261014_export_qditp_user.sql"),
                ("QDITP_RIDE", "20261014_export_qditp_ride.sql"),
                ("QDITP_TXN", "20261014_export_qditp_txn.sql")]:
    txt = open(os.path.join(MIG, fn), encoding="utf-8").read()
    # tables= 行（PROMPT 续行到 parallel=）
    seg = re.search(r"tables\s*=\s*(.*?)parallel", txt, re.S)
    if seg:
        names = set(re.findall(r"QDITP\.([A-Z0-9_]+)", seg.group(1)))
        export_lists[dom] = names

# ---------- 2. DDL 块解析（字符级扫描，正确处理引号内的分号） ----------
def extract_blocks(text):
    """返回 [(kind, name, block_text)]，kind ∈ TABLE/SEQUENCE/INDEX"""
    out = []
    # 找所有 create 语句起点（行首，允许缩进；排除注释行；跳过 schema. 前缀取真实对象名）
    pat = re.compile(r"(?im)^\s*create\s+(unique\s+index|index|table|sequence)\s+(if\s+not\s+exists\s+)?(?:[\"']?[A-Za-z0-9_]+[\"']?\.)?[\"']?([A-Za-z0-9_]+)[\"']?")
    for m in pat.finditer(text):
        kind = {"unique index": "INDEX", "index": "INDEX", "table": "TABLE", "sequence": "SEQUENCE"}[m.group(1).lower()]
        name = m.group(3).upper()
        start = m.start()
        # 从 start 扫描到语句结束的 ; （跟踪单引号）
        i, in_q, end = start, False, None
        while i < len(text):
            c = text[i]
            if c == "'":
                if in_q and i + 1 < len(text) and text[i+1] == "'":
                    i += 2; continue
                in_q = not in_q
            elif c == ';' and not in_q:
                end = i + 1; break
            i += 1
        if end is None:
            continue
        block = text[start:end]
        # 附带紧随其后的 comment on ...; 语句
        tail = text[end:]
        cm = re.match(r"\s*(comment\s+on\s+[^;]*;)+", tail, re.I | re.S)
        if cm:
            block += cm.group(0)
        out.append((kind, name, block.rstrip()))
    return out

# 源文件 → 分类
SRC = []
for base, sub in [("acc-es-server", "acc-es-server-schema.sql"),
                  ]:
    pass
src_files = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    dirnames[:] = [d for d in dirnames if d not in ("target", ".svn", "node_modules", ".git", "web")]
    for fn in filenames:
        if not fn.endswith(".sql"):
            continue
        p = os.path.join(dirpath, fn)
        rel = os.path.relpath(p, ROOT)
        if rel.startswith("scripts/migration/2026101"):
            continue  # 迁移执行包自身不是 DDL 源
        src_files.append(rel)
# web-server/sql 单列（walk 排除了 web 目录）；只收 Oracle 方言文件
# （ry_20250522.sql / quartz.sql 是 MySQL 原版，NEVER 入池——曾因优先级打平被误选）
for fn in os.listdir(os.path.join(ROOT, "web-server/sql")):
    if fn.endswith(".sql") and ("_oracle" in fn or "-menu" in fn or "permission" in fn
                                or "migration" in fn or "complete" in fn):
        src_files.append("web-server/sql/" + fn)

MYSQL_MARK = re.compile(r"auto_increment|engine\s*=|`", re.I)
def is_oracle_dialect(block):
    return not MYSQL_MARK.search(block)

def classify(rel):
    if "ry_20250522" in rel: return "ruoyi"
    if "quartz" in rel: return "quartz"
    if rel.startswith("scripts/"): return "script"
    if "-schema.sql" in rel: return "base"
    return "migration"

table_src = {}   # table -> list of (rel, kind, block)
seq_src = {}     # seq -> list of (rel, block)
idx_src = defaultdict(list)  # table -> [(rel, block)]
for rel in sorted(src_files):
    try:
        text = open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace").read()
    except Exception as e:
        print("SKIP", rel, e); continue
    for kind, name, block in extract_blocks(text):
        if kind == "TABLE":
            table_src.setdefault(name, []).append((rel, classify(rel), block))
        elif kind == "SEQUENCE":
            seq_src.setdefault(name, []).append((rel, block))
        else:
            idx_src[name].append((rel, block))

# ---------- 3. 选源策略：base > ruoyi/quartz > script > migration ----------
PRIO = {"base": 0, "ruoyi": 1, "quartz": 1, "script": 2, "migration": 3}
def pick(name):
    cands = table_src.get(name, [])
    if not cands:
        return None
    # 同优先级内：Oracle 方言优先（防 MySQL 原版混入），再按来源稳定性排序
    cands = sorted(cands, key=lambda c: (PRIO[c[1]], 0 if is_oracle_dialect(c[2]) else 1, c[0]))
    return cands[0]

# ---------- 4. 外键依赖排序 ----------
def refs_of(block):
    return set(t.upper() for t in re.findall(r"references\s+[\"']?([A-Za-z0-9_]+)", block, re.I))

def topo(tables):
    """tables: list of (name, block)；按 FK 依赖排序，环则保序并报告"""
    byname = {n: b for n, b in tables}
    deps = {n: (refs_of(b) & set(byname)) - {n} for n, b in tables}
    ordered, cycles, seen = [], [], set()
    def visit(n, stack):
        if n in seen: return
        if n in stack:
            cycles.append(list(stack[stack.index(n):]) + [n]); return
        for d in sorted(deps.get(n, ())):
            visit(d, stack + [n])
        seen.add(n); ordered.append(n)
    for n, _ in tables:
        visit(n, [])
    return ordered, cycles

# ---------- 5. 序列归属：按定义文件的表的多数域 ----------
file_tables = defaultdict(set)
for t, lst in table_src.items():
    for rel, _, _ in lst:
        file_tables[rel].add(t)
dom_of_table = {}
for dom, names in domains.items():
    for n in names:
        dom_of_table[n] = dom

seq_domain = {}
for seq, lst in seq_src.items():
    rel = lst[0][0]
    votes = defaultdict(int)
    for t in file_tables.get(rel, ()):
        d = dom_of_table.get(t)
        if d: votes[d] += 1
    if votes:
        seq_domain[seq] = max(votes, key=votes.get)

# ---------- 6. 生成 ----------
DOMAIN_ORDER = ["QDITP_PLAT", "QDITP_USER", "QDITP_RIDE", "QDITP_TXN"]
TS_OF = {"QDITP_PLAT": "ITP_PLAT_TS", "QDITP_USER": "ITP_USER_TS",
         "QDITP_RIDE": "ITP_RIDE_TS", "QDITP_TXN": "ITP_TXN_TS"}

manifest = []
manifest.append("# 分域 Schema 清单与建表脚本（v1.0 · 2026-10-17）\n")
manifest.append("> 由 `scripts/migration/20261017_schema_gen.py` 生成；域清单权威源 = `20261014_bundle_m2_m3_migration.sql` 的 IN 列表，与 4 个 export 脚本 `tables=` 交叉校验。")
manifest.append("> DDL 来源 = 仓库本地 SQL（21 个 `*-schema.sql` + migration + RuoYi + quartz + scripts 种子脚本），**NEVER 从老库 GET_DDL**（防老库漂移）；老库仅用于数据导出与结构 diff。")
manifest.append("> 目标 = 红岛生产 `172.120.110.25:1521/hditpdb`（PDB）。执行 MUST 用各域用户（无 TABLESPACE 子句 → 落 M1 绑定的默认表空间）。\n")
manifest.append("## 执行顺序与方式\n")
manifest.append("```bash")
manifest.append("# 顺序 MUST：PLAT → USER → RIDE → TXN（跨域外键/同义词依赖；PLAT 是被引用方）")
for dom in DOMAIN_ORDER:
    manifest.append(f"sqlplus {dom}/<pwd>@//172.120.110.25:1521/hditpdb @scripts/migration/20261017_schema_{dom.split('_')[1].lower()}.sql")
manifest.append("```")
manifest.append("- MUST 以域用户执行：DDL 无 `TABLESPACE` 子句，表落**执行用户的默认表空间**（M1 已绑定 `ITP_<域>_TS`）；**NEVER 用 SYS 执行**（落到 SYSTEM 且 owner 错）")
manifest.append("- 幂等：重复执行报 `ORA-00955`（名称已存在）可忽略；单表失败不阻断（sqlplus 默认继续），但 MUST 回查输出")
manifest.append("- 建表后：灌数（跨库脚本 Phase 5）→ 序列校准（Phase 6.1）→ 重跑 M1 Step 5/6/7 授权 + 菜单种子（Phase 6.7）\n")

summary = {}
for dom in DOMAIN_ORDER:
    names = domains[dom]
    picked, missing, only_migration = [], [], []
    for n in names:
        c = pick(n)
        if c is None:
            missing.append(n)
        else:
            picked.append((n, c))
            kinds = {k for _, k, _ in table_src[n]}
            if kinds == {"migration"}:
                only_migration.append((n, c[0]))
    ordered, cycles = topo([(n, b) for n, (_, _, b) in picked])
    summary[dom] = dict(total=len(names), picked=len(picked), missing=missing,
                        only_migration=only_migration, cycles=cycles)

    dom_short = dom.split("_")[1].lower()
    fn = os.path.join(MIG, f"20261017_schema_{dom_short}.sql")
    with open(fn, "w", encoding="utf-8") as f:
        f.write(f"-- {'='*75}\n")
        f.write(f"-- 青岛地铁 ITP 分库迁移 — {dom} 域建表脚本（v1.0 · 2026-10-17）\n")
        f.write(f"-- 目标：红岛生产 hditpdb PDB，schema = {dom}，默认表空间 = {TS_OF[dom]}\n")
        f.write(f"-- 表数：{len(picked)}/{len(names)}（缺 {len(missing)}，见 20261017_schema_manifest.md）\n")
        f.write(f"-- 执行：sqlplus {dom}/<pwd>@//172.120.110.25:1521/hditpdb @20261017_schema_{dom_short}.sql\n")
        f.write(f"--   - MUST 以 {dom} 用户执行（无 TABLESPACE 子句 → 落默认表空间 {TS_OF[dom]}）\n")
        f.write(f"--   - NEVER 用 SYS；重复执行 ORA-00955 可忽略；分区表的分区上界见文件末注\n")
        f.write(f"-- 前置：M1 已完成（{dom} 用户 + {TS_OF[dom]} 存在）；PLAT MUST 先于 USER/RIDE/TXN\n")
        f.write(f"-- {'='*75}\n\n")
        if cycles:
            f.write(f"-- ⚠️ 外键环（已按出现顺序执行，如报 ORA-00904/ORA-02270 先建表后补约束）：{cycles}\n\n")
        # 本域序列（按文件归属）
        dom_seqs = [s for s, d in seq_domain.items() if d == dom]
        for n in ordered:
            rel, kind, block = next((r, k, b) for nn, (r, k, b) in picked if nn == n)
            f.write(f"-- [来源] {rel}（{kind}）\n{block}\n\n")
        for s in sorted(dom_seqs):
            rel, block = seq_src[s][0]
            f.write(f"-- [序列] {rel}\n{block}\n\n")
        # 索引（若源文件有独立 create index）
        for n in ordered:
            for rel, block in idx_src.get(n, []):
                if pick(n) and pick(n)[0] == rel:
                    f.write(f"-- [索引] {rel}\n{block}\n\n")
        f.write(f"-- {'='*75}\n")
        f.write(f"-- {dom} 域完成。缺表 {len(missing)} 张：{missing}\n")
        f.write(f"-- {'='*75}\n")
    print(f"[生成] {fn}  表 {len(picked)}/{len(names)}  序列 {len(dom_seqs)}  缺 {len(missing)}")

# ---------- 7. 清单正文 ----------
manifest.append("## 总览\n")
manifest.append("| 域 | 目标 schema | 默认表空间 | 清单表数 | 本地有 DDL | 缺 DDL（需补） |")
manifest.append("|---|---|---|---|---|---|")
for dom in DOMAIN_ORDER:
    s = summary[dom]
    manifest.append(f"| {dom.split('_')[1]} | {dom} | {TS_OF[dom]} | {s['total']} | {s['picked']} | {len(s['missing'])} |")
manifest.append("")

manifest.append("## 逐域明细\n")
for dom in DOMAIN_ORDER:
    s = summary[dom]
    manifest.append(f"### {dom.split('_')[1]} 域（{s['picked']}/{s['total']}）\n")
    manifest.append("| # | 表 | DDL 来源 | 类型 |")
    manifest.append("|---|---|---|---|")
    for i, n in enumerate(sorted(domains[dom]), 1):
        c = pick(n)
        if c:
            rel, kind, _ = c
            flag = " ⚠️仅migration" if (n, rel) in [(x, y) for x, y in s["only_migration"]] else ""
            manifest.append(f"| {i} | {n} | {rel} | {kind}{flag} |")
        else:
            manifest.append(f"| {i} | {n} | **缺失** | ❌ |")
    manifest.append("")

manifest.append("## 缺口与未分域（MUST 人工裁决）\n")
all_missing = []
for dom in DOMAIN_ORDER:
    for n in summary[dom]["missing"]:
        all_missing.append((dom, n))
if all_missing:
    manifest.append("### 域清单内有、本地无 DDL（需从源库 GET_DDL 或补录）\n")
    manifest.append("| 域 | 表 | 建议 |")
    manifest.append("|---|---|---|")
    for dom, n in all_missing:
        manifest.append(f"| {dom.split('_')[1]} | {n} | 源库 `DBMS_METADATA.GET_DDL('TABLE','{n}','QDITP')` 取回审核后入仓 |")
    manifest.append("")
assigned = set()
for dom in DOMAIN_ORDER:
    assigned |= set(domains[dom])
unassigned = sorted(set(table_src) - assigned)
if unassigned:
    manifest.append("### 本地有 DDL、不在任何域清单（未分域，待裁决）\n")
    manifest.append("| 表 | 来源 | 建议归属（待确认） |")
    manifest.append("|---|---|---|")
    RECOMMEND = {
        "SYS_JOB_LOG": "PLAT（RuoYi 配套）", "SYS_ROLE_DEPT": "PLAT（RuoYi 配套）",
        "SYS_ROLE_MENU": "PLAT（RuoYi 配套）", "SYS_USER_POST": "PLAT（RuoYi 配套）",
        "SYS_USER_ROLE": "PLAT（RuoYi 配套）", "QRTZ_SIMPROP_TRIGGERS": "PLAT（Quartz 配套）",
        "AGM_DEVICE_STATUS": "PLAT（设备基础设施，同 COM_DEVICE_SYN_KEY）",
        "GATEWAY_ROUTE_CONFIG": "PLAT（网关路由配置，基础设施）",
        "GATEWAY_ROUTE_CONFIG_AUDIT": "PLAT（网关路由审计，随主表）",
        "STATION_INFO": "**不迁**（已退场，RIDE 域用同义词指向 PLAT.TBL_STATION_INFO）",
    }
    for n in unassigned:
        rel = table_src[n][0][0]
        manifest.append(f"| {n} | {rel} | {RECOMMEND.get(n, '待裁决')} |")
    manifest.append("")

# export 交叉校验差异
manifest.append("## 域清单交叉校验（bundle IN 列表 vs export tables=）\n")
for dom in DOMAIN_ORDER:
    b, e = set(domains[dom]), export_lists.get(dom, set())
    only_b, only_e = sorted(b - e), sorted(e - b)
    if only_b or only_e:
        manifest.append(f"- **{dom}**：仅 bundle 有 {only_b}；仅 export 有 {only_e}")
    else:
        manifest.append(f"- {dom}：一致（{len(b)} 张）")
manifest.append("")

# 外键环
cyc = [(d, c) for d in DOMAIN_ORDER for c in summary[d]["cycles"]]
if cyc:
    manifest.append("## 外键环\n")
    for d, c in cyc:
        manifest.append(f"- {d}：{' → '.join(c)}")
    manifest.append("")

manifest.append("## 分区表特别注记（导入后 MUST 处理）\n")
manifest.append("- `USER_ITP_REG_INFO`：LIST 分区（THIRD_USER_ID_SUFFIX，P00~P99），本地 DDL 完整，随建随用")
manifest.append("- 8 张 RANGE 分区表（GATE_TXN_PAY / QRCODE_TXN_DETAIL / ALIPAY_PAY_TXN_DETAIL / ALIPAY_REFUND_TXN_DETAIL / ALIPAY_PAY_CALLBACK_LOG / ALIPAY_PAY_CENTER_MSG_LOG / F2F_ORDER / F2F_PAYMENT / F2F_RESULT_REPORT / PAY_TXN_DETAIL）：本地 DDL 的分区上界以文件为准，若仍到 `P202612`，灌数后 MUST 按 M0 补分区（跨库脚本 Phase 6.5）")
manifest.append("- F2F_* 的 GLOBAL 唯一索引随块内联（保持现状；GLOBAL→LOCAL 是 M0 裁定项）\n")

with open(os.path.join(MIG, "20261017_schema_manifest.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(manifest))
print("[生成] 20261017_schema_manifest.md")

# ---------- 8. 汇总输出 ----------
print("\n===== 汇总 =====")
for dom in DOMAIN_ORDER:
    s = summary[dom]
    print(f"{dom}: {s['picked']}/{s['total']} 缺{len(s['missing'])} {s['missing']}")
print("未分域:", len(unassigned), unassigned)
print("仅 migration 有 DDL:", [(d, n) for d in DOMAIN_ORDER for n, r in summary[d]['only_migration']])
print("序列总数:", len(seq_src), "归属:", {k: v for k, v in seq_domain.items()})

# 方言校验：所有入选块 + 所有序列块 MUST 是 Oracle 方言
bad = []
for dom in DOMAIN_ORDER:
    for n in domains[dom]:
        c = pick(n)
        if c and not is_oracle_dialect(c[2]):
            bad.append((n, c[0]))
for s, lst in seq_src.items():
    for rel, block in lst:
        if not is_oracle_dialect(block):
            bad.append((s, rel))
print("MySQL 方言残留:", bad if bad else "无 ✅")
