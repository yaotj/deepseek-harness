#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 docs/sql/01_qditp_ddl.sql + 02_qditp_data.sql（2026-11-12 10:15/10:16 测试库 QDITP 全量导出）
按域拆成「分库后的建表脚本 + 数据导入脚本 + 灌数后校准脚本」。

与 2026-10-23 版（deploy/sche/by-domain/）的区别：
  - 源导出**已去除 schema 前缀与 TABLESPACE 子句**，且**已含 248 条独立索引**（旧版缺 169 个，靠本地 DDL 补）；
  - 源导出按「4 个部分」组织（建表 / 索引 / 注释 / 约束），不是按表分块 → 本脚本按语句块解析后按域重组；
  - 本次导出多 12 张表（6 张 11 月新增业务表 + 6 张新 BAK_* 快照），见 EXTRA_DOMAIN / EXTRA_EXCLUDED。

域映射：
  - 基线 = docs/ops/environments/scripts/migration/domain_map.py（2026-10-23 四域清单，175 张）。
    **NEVER 直接改 domain_map.py** —— 10-23 两个拆分脚本断言「映射 == 175 对象全覆盖」，
    加新表会让它们重跑失败。本次增量映射只记在本脚本 + 输出 README，待 DBA / 业务确认后回写。
  - 6 张新增业务表按代码归属推定，全部归 PLAT：
    ASYNC_EXPORT_TASK(web-admin 异步导出) / GRAYLIST, GRAYLIST_OPERATE_LOG,
    HCE_BLACKLIST_FILE_STATE(blacklist-server) / RECON_UPLOAD_FILE(web-server 综管台对账上传) /
    TBL_DEVICE_INFO(para-server)。**归属属推定，MUST 经 DBA / 业务确认**。

变换：
  - 所有对象加显式 "QDITP_<域>". 前缀（CREATE TABLE / INDEX / COMMENT ON / ALTER TABLE / INSERT INTO），
    与 deploy/sche/by-domain/ 红岛版口径一致；文件头另设 ALTER SESSION SET CURRENT_SCHEMA 双保险。
  - 每个域文件内部顺序 = 建表 → 独立索引 → 注释 → 约束（与源四部分一致，NEVER 调换：
    第 4 部分有 USING INDEX 引用第 2 部分建的索引）。
  - 数据按域内**外键拓扑序**输出（父表先入），避免 QRTZ_* / RECON_* / LOGIC_CARD_POOL_* 的 FK 违例。
  - 2 张 GENERATED ALWAYS AS IDENTITY 且数据带显式 ID 的表，建表时临时降为 BY DEFAULT；
    灌数后由 03_post_load.sql 恢复 ALWAYS 并把全部 identity 列校准到 START WITH LIMIT VALUE。

执行纪律：本脚本只生成本地文件，**NEVER 连库、NEVER 执行任何 SQL**。
输出：docs/sql/by-domain/{01_ddl,02_data}/QDITP_<域>.sql + 03_post_load.sql + _未纳入_*.sql + README.md

可重跑：python3 docs/ops/environments/scripts/migration/20261112_split_qditp_by_domain.py
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from domain_map import DOMAIN, EXCLUDED, DOMAINS  # noqa: E402

REPO = Path(__file__).resolve().parents[5]
SRC_DDL = REPO / "docs/sql/01_qditp_ddl.sql"
SRC_DATA = REPO / "docs/sql/02_qditp_data.sql"
OUT = REPO / "docs/sql/by-domain"
DDL_OUT = OUT / "01_ddl"
DATA_OUT = OUT / "02_data"
for d in (DDL_OUT, DATA_OUT):
    d.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- 增量域映射
EXTRA_DOMAIN = {
    "ASYNC_EXPORT_TASK": "PLAT",
    "GRAYLIST": "PLAT",
    "GRAYLIST_OPERATE_LOG": "PLAT",
    "HCE_BLACKLIST_FILE_STATE": "PLAT",
    "RECON_UPLOAD_FILE": "PLAT",
    "TBL_DEVICE_INFO": "PLAT",
}
EXTRA_EXCLUDED = {
    "BAK_SYS_DEPT_20261106", "BAK_SYS_JOB_20261106", "BAK_SYS_MENU_20261104",
    "BAK_SYS_MENU_20261106", "BAK_SYS_POST_20261106", "BAK_SYS_ROLE_MENU_20261104",
}
ALL_DOMAIN = dict(DOMAIN)
ALL_DOMAIN.update(EXTRA_DOMAIN)
ALL_EXCLUDED = set(EXCLUDED) | EXTRA_EXCLUDED
assert not (set(ALL_DOMAIN) & ALL_EXCLUDED), "入域表与排除表重叠"

SCHEMA = {d: f"QDITP_{d}" for d in DOMAINS}

# ---------------------------------------------------------------- 账号与口令（2026-11-12 裁定：与红岛一致）
# 用户裁定：各域命名（schema / 表空间）与口令与红岛一致。口令 MUST 从唯一真源动态解析，
# NEVER 在本脚本硬编码 —— 改密只需改 `跨环境共性.md` §三真源表 + 重跑本生成器，产物不会分叉。
CRED_SRC = REPO / "docs/ops/environments/跨环境共性.md"
WANTED_USERS = {"QDITP_PLAT", "QDITP_USER", "QDITP_RIDE", "QDITP_TXN", "itp_ro"}
CREDS = {}
for _line in CRED_SRC.read_text(encoding="utf-8").splitlines():
    if not _line.startswith("| 红岛生产 |"):
        continue
    _cells = [c.strip() for c in _line.split("|")]
    if len(_cells) >= 6:
        _u, _p = _cells[3].strip("`"), _cells[4].strip("`")
        if _u in WANTED_USERS:
            CREDS[_u] = _p
assert set(CREDS) == WANTED_USERS, (
    f"口令真源解析不全：{sorted(WANTED_USERS - set(CREDS))}（检查跨环境共性.md §3.1 表格格式）")

# ---------------------------------------------------------------- 读源
ddl_text = SRC_DDL.read_text(encoding="utf-8")
data_text = SRC_DATA.read_text(encoding="utf-8")

# 源 DDL 按「第 N 部分」切段（标题形如 -- ====== 第 1 部分: 建表语句 ======）
sections = re.split(r"(?m)^--\s*=+\s*第\s*(\d)\s*部分:.*$", ddl_text)
assert len(sections) == 9, f"DDL 未切出 4 个部分，实际 {len(sections)} 段"
part = {int(sections[i]): sections[i + 1] for i in range(1, len(sections), 2)}
assert set(part) == {1, 2, 3, 4}, "DDL 部分编号异常"


def stmts(body, lookahead):
    """按行首语句关键字切块；保留块前的 -- 表 X -- 注释行。"""
    chunks = [c for c in re.split(lookahead, body) if c.strip()]
    return [c for c in chunks if not re.match(r"\s*--\s*=+", c)]


# 第 1 部分每条 CREATE TABLE 前恰有一个 `-- ------ 表 X ------` 标题（187 个），按标题切分最干净；
#   其中 2 张表（ALIPAY_SIGN_INFO / GATE_TXN_PAY）的块内还带 1 条 CREATE INDEX +
#   1 条 USING INDEX 的 PK / UNIQUE 约束，随本表块一起分流、一起加前缀。
# 第 2 部分 = 246 条纯 CREATE [UNIQUE] INDEX；第 3 部分 1351 条注释；第 4 部分 10 条外键。
p1 = stmts(part[1], r'(?m)^(?=-- ------ 表 [A-Z0-9_]+ ------)')
p2 = stmts(part[2], r'(?m)^(?=[ \t]*CREATE (?:UNIQUE )?INDEX \")')
p3 = stmts(part[3], r'(?m)^(?=[ \t]*COMMENT ON )')
p4 = stmts(part[4], r'(?m)^(?=[ \t]*ALTER TABLE ")')
assert (len(p1), len(p2), len(p3), len(p4)) == (187, 246, 1351, 10), (
    "语句块数与源导出不符：%s" % ((len(p1), len(p2), len(p3), len(p4)),))


def owner_of(chunk, kind):
    if kind == 1:
        m = re.search(r'CREATE TABLE "([A-Z0-9_]+)"', chunk)
    elif kind == 2:
        # 第 2 部分主体是 CREATE INDEX，另含 2 条 USING INDEX 约束（ALTER TABLE）
        m = re.search(r'CREATE (?:UNIQUE )?INDEX "[^"]+"\s+ON\s+"([A-Z0-9_]+)"', chunk, re.S)
        if not m:
            m = re.search(r'ALTER TABLE\s+"([A-Z0-9_]+)"', chunk)
    elif kind == 3:
        m = re.search(r'COMMENT ON (?:TABLE|COLUMN)\s+"([A-Z0-9_]+)"', chunk)
    else:
        m = re.search(r'ALTER TABLE\s+"([A-Z0-9_]+)"', chunk)
    assert m, "无法解析语句所属表：%s" % chunk[:120]
    return m.group(1)


buckets = {k: defaultdict(list) for k in (1, 2, 3, 4)}
excluded_chunks = defaultdict(list)
for kind, group in ((1, p1), (2, p2), (3, p3), (4, p4)):
    for chunk in group:
        t = owner_of(chunk, kind)
        if t in ALL_DOMAIN:
            buckets[kind][ALL_DOMAIN[t]].append((t, chunk))
        elif t in ALL_EXCLUDED:
            excluded_chunks[kind].append((t, chunk))
        else:
            raise AssertionError(f"表 {t} 未在域映射中（第 {kind} 部分）")

# 断言：DDL 表全集 == 映射全集；四类语句都归到正确域
ddl_tables = {owner_of(c, 1) for c in p1}
assert len(p1) == len(ddl_tables) == 187, "CREATE TABLE 有重名"
assert ddl_tables == (set(ALL_DOMAIN) | ALL_EXCLUDED), (
    f"映射与导出不一致：多 {sorted((set(ALL_DOMAIN) | ALL_EXCLUDED) - ddl_tables)}，"
    f"少 {sorted(ddl_tables - (set(ALL_DOMAIN) | ALL_EXCLUDED))}")

# 断言：外键零跨域；USING INDEX 引用的索引名 MUST 在本域第 2 部分里存在
for dom in DOMAINS:
    dom_idx = set()
    for _, c in buckets[1][dom] + buckets[2][dom]:
        for m in re.finditer(r'(?m)^[ \t]*CREATE (?:UNIQUE )?INDEX "([A-Z0-9_]+)"', c):
            dom_idx.add(m.group(1))
    # 第 1 部分块内可能带 USING INDEX 约束，也要校验引用的索引在本域
    extra_alters = [x for x in buckets[1][dom] if "USING INDEX" in x[1]]
    for t, c in buckets[4][dom] + extra_alters:
        ref = re.search(r'REFERENCES\s+"([A-Z0-9_]+)"', c)
        if ref:
            assert ALL_DOMAIN.get(ref.group(1)) == dom, (
                f"跨域外键：{dom}.{t} -> {ref.group(1)}（必须同域或改服务化）")
        ui = re.search(r'USING INDEX\s+"([A-Z0-9_]+)"', c)
        if ui:
            assert ui.group(1) in dom_idx, (
                f"{dom}.{t} 的 USING INDEX {ui.group(1)} 不在本域独立索引清单里")

# ---------------------------------------------------------------- 前缀改写
def qualify(chunk, schema, kind):
    p = f'"{schema}".'
    out = chunk
    out = re.sub(r'(CREATE TABLE\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"', out, count=1)
    out = re.sub(r'(CREATE (?:UNIQUE )?INDEX\s+)"([A-Z0-9_]+)"(\s+ON\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"{m.group(3)}{p}"{m.group(4)}"',
                 out, count=1, flags=re.S)
    out = re.sub(r'(COMMENT ON (?:TABLE|COLUMN)\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"', out, count=1)
    out = re.sub(r'(ALTER TABLE\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"', out, count=1)
    out = re.sub(r'(REFERENCES\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"', out, count=1)
    out = re.sub(r'(USING INDEX\s+)"([A-Z0-9_]+)"',
                 lambda m: f'{m.group(1)}{p}"{m.group(2)}"', out, count=1)
    return out


# ---------------------------------------------------------------- identity / 虚拟列
# 列定义形如 `"ID" NUMBER(19,0) GENERATED ALWAYS AS IDENTITY ...`——NUMBER 精度里带逗号，
# 所以中间 MUST 用 [^\n]*?（不跨行即可），NEVER 写 [^,\n]*?（会在 NUMBER(19,0) 处提前截断）。
IDENTITY_RE = re.compile(
    r'"([A-Z0-9_]+)"[^\n]*?GENERATED\s+(ALWAYS|BY DEFAULT(?: ON NULL)?) AS IDENTITY')
identities = []       # (table, col, kind)
always_tables = {}    # table -> col
for t, c in [(owner_of(x, 1), x) for x in p1]:
    for line in c.splitlines():
        m = IDENTITY_RE.search(line)
        if m:
            identities.append((t, m.group(1), m.group(2)))
            if m.group(2) == "ALWAYS":
                always_tables[t] = m.group(1)
assert len(identities) == 37, f"identity 列解析数异常（应为 37）：{len(identities)}"
assert set(always_tables) == {"APP_PAY_SIGN_REQUEST", "GATEWAY_ROUTE_CONFIG_AUDIT"}, (
    f"ALWAYS identity 表与实测不符：{sorted(always_tables)}")

# ---------------------------------------------------------------- 数据分块
data_parts = re.split(r"(?m)^(-- ------ 表 ([A-Z0-9_]+) ------)\s*$", data_text)
assert len(data_parts) > 2, "数据文件未按 -- 表 X -- 分块"
data_blocks = {}
for i in range(1, len(data_parts), 3):
    header, name, body = data_parts[i], data_parts[i + 1], data_parts[i + 2]
    assert name not in data_blocks, f"数据表块重名：{name}"
    data_blocks[name] = (header, body)
assert set(data_blocks) <= ddl_tables, f"数据块有 DDL 中不存在的表：{sorted(set(data_blocks) - ddl_tables)}"

insert_re = re.compile(r"(?m)^\s*INSERT\s+INTO\s+")
n_insert_src = {t: len(insert_re.findall(b)) for t, (_, b) in data_blocks.items()}
assert sum(n_insert_src.values()) == 105845, "源 INSERT 总数与导出基线不符"

# 域内外键拓扑序（父表先入）
edges = defaultdict(set)
for dom in DOMAINS:
    for t, c in buckets[4][dom]:
        ref = re.search(r'REFERENCES\s+"([A-Z0-9_]+)"', c)
        if ref and ref.group(1) != t and ALL_DOMAIN.get(ref.group(1)) == dom:
            edges[t].add(ref.group(1))


def topo(dom):
    tables = sorted(t for t, d in ALL_DOMAIN.items() if d == dom)
    in_dom = set(tables)
    order, done = [], set()
    remaining = list(tables)
    while remaining:
        ready = [t for t in remaining if not (edges[t] & (in_dom - done))]
        if not ready:                      # 环：回退字母序并告警
            print(f"  WARN: {dom} 域外键存在环，{sorted(remaining)} 按字母序输出")
            ready = remaining[:1]
        for t in ready:
            order.append(t)
            done.add(t)
            remaining.remove(t)
    return order


DDL_HEADER = """-- ============================================================================
-- {schema} 域建表脚本（分库后 / {n_tables} 张表）
-- 来源：docs/sql/01_qditp_ddl.sql（2026-11-12 10:15 测试库 QDITP 全量导出，187 张表）
-- 生成：docs/ops/environments/scripts/migration/20261112_split_qditp_by_domain.py（可重跑）
-- 本域：建表 {n_tables} / 独立索引 {n_idx} / 注释 {n_cmt} / 外键约束 {n_fk}
--       （USING INDEX 的 PK / UNIQUE 约束随对应建表块一同输出，不计入外键数）
-- 执行纪律（MUST）：
--   1. 以域用户执行（账号 / 口令已按 2026-11-12 裁定与红岛一致；口令含 !/# MUST 单引号包裹）：
--      sqlplus '{schema}/{pwd}@//<目标VIP>:1521/<服务名>' @{schema}.sql
--      NEVER 用 SYS / 其它域账号；对象均带显式 "{schema}". 前缀，连错账号会报权限错误。
--      连接串（VIP / 服务名 / 端口）MUST 现查目标环境，NEVER 抄红岛 / 测试值。
--      口令真源 docs/ops/environments/跨环境共性.md「三、敏感值真源」；改密 MUST 重跑生成器。
--   2. 四域顺序 MUST：PLAT → USER → RIDE → TXN（PLAT 是被引用方）。
--   3. 本文件内部顺序 = 建表（含 2 张表的 USING INDEX 约束）→ 独立索引 → 注释 → 外键约束，
--      **NEVER 调换**：约束 MUST 在表与其索引都建好之后才添加。
--   4. 目标库 MUST 先完成 M1 基础设施（建 4 个域用户 + 绑定 ITP_<域>_TS 默认表空间）；
--      源导出已去除 TABLESPACE 子句，表落执行用户默认表空间。
--   5. 全库 2 张 GENERATED ALWAYS identity 表已临时降为 BY DEFAULT（本域见末行清单），
--      灌数后 MUST 执行 03_post_load.sql 恢复 ALWAYS + 校准全部 37 个 identity 列。
--   6. 生产执行前 MUST 二次确认 + DBA 审批 + 备份；本机生成物 NEVER 直接执行。
--   7. WHENEVER SQLERROR CONTINUE 会静默跳过失败语句 → 执行后 MUST 跑 README 的校验 SQL。
-- 本文件临时降级的 identity 表：{always_list}
-- ============================================================================
SET DEFINE OFF
SET SQLBLANKLINES ON
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET CURRENT_SCHEMA = {schema};

"""

DATA_HEADER = """-- ============================================================================
-- {schema} 域数据导入文件（{n_data} 张表有数据 / {n_insert} 条 INSERT）
-- 来源：docs/sql/02_qditp_data.sql（2026-11-12 10:16 测试库 QDITP 数据导出，共 105845 条 INSERT）
-- 生成：docs/ops/environments/scripts/migration/20261112_split_qditp_by_domain.py（可重跑）
-- 执行纪律（MUST）：
--   1. MUST 先成功执行 01_ddl/{schema}.sql（表 + 索引 + 约束）再导数据。
--   2. 以域用户执行：sqlplus '{schema}/{pwd}@//<目标VIP>:1521/<服务名>' @{schema}.sql
--      （账号 / 口令按 2026-11-12 裁定与红岛一致；口令含 !/# MUST 单引号包裹，
--        真源 docs/ops/environments/跨环境共性.md「三、敏感值真源」，改密 MUST 重跑生成器）
--      NEVER 用 SYS / 其它域账号；INSERT 均带显式 "{schema}". 前缀。
--   3. 表顺序已按域内外键拓扑排序（父表先入）；若仍报 FK 违例（存量脏数据），
--      先 DISABLE 约束 → 导完 → ENABLE VALIDATE。
--   4. 幂等：重复导入必然主键/唯一键冲突；重导前 MUST TRUNCATE 或换库，NEVER 直接重跑。
--   5. 导完 MUST 执行 03_post_load.sql（identity 校准 + 恢复 ALWAYS），否则后台首次新增即主键冲突。
--   6. 大事务注意 undo：文件内按表 COMMIT；SET DEFINE OFF 防数据里的 & 被当替换变量。
--   7. 生产执行前 MUST 二次确认 + DBA 审批 + 备份；本机生成物 NEVER 直接执行。
-- 虚拟列（QRCODE_STATUS.CARD_ID_TAIL / USER_ITP_REG_INFO.THIRD_USER_ID_SUFFIX）
--   已由源导出自动排除在 INSERT 列清单外，NEVER 手工补回。
-- ============================================================================
SET DEFINE OFF
SET SQLBLANKLINES ON
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET CURRENT_SCHEMA = {schema};

"""

# ---------------------------------------------------------------- 写 DDL
ddl_stats = {}
for dom in DOMAINS:
    schema = SCHEMA[dom]
    # 第 1 部分有 2 张表的块内自带 CREATE INDEX，独立索引总数 MUST 把它们算进来
    n_idx = len(buckets[2][dom]) + sum(
        len(re.findall(r'(?m)^[ \t]*CREATE (?:UNIQUE )?INDEX "', c)) for _, c in buckets[1][dom])
    groups = []
    for kind, title in ((1, "第 1 部分：建表"),
                        (2, "第 2 部分：独立索引（含 USING INDEX 的 PK / UNIQUE 约束）"),
                        (3, "第 3 部分：表与字段注释"),
                        (4, "第 4 部分：外键约束")):
        body = []
        for t, chunk in buckets[kind][dom]:
            out = qualify(chunk, schema, kind)
            if kind == 1 and t in always_tables:
                out = out.replace("GENERATED ALWAYS AS IDENTITY",
                                  "GENERATED BY DEFAULT AS IDENTITY")
            assert ";" in out, f"{t} 语句块缺分号（源导出格式已变，NEVER 自动补）"
            body.append(out.rstrip())
        groups.append(f"-- ==================== {title}（{len(body)} 条） ====================\n\n"
                      + "\n".join(body) + "\n")
    always_list = "、".join(f"{t}.{c}" for t, c in sorted(always_tables.items())
                           if ALL_DOMAIN.get(t) == dom) or "（本域无）"
    head = DDL_HEADER.format(schema=schema, pwd=CREDS[schema], n_tables=len(buckets[1][dom]),
                             n_idx=n_idx, n_cmt=len(buckets[3][dom]),
                             n_fk=len(buckets[4][dom]), always_list=always_list)
    text = head + "\n".join(groups)
    # 自检：本域文件里不能出现其它域的 schema 前缀；CREATE TABLE 数守恒
    for other in DOMAINS:
        if other != dom:
            assert f'"{SCHEMA[other]}".' not in text, f"{dom} 域混入 {other} 域对象"
    assert len(re.findall(r'(?m)^CREATE TABLE "' + re.escape(schema) + r'"\."', text)) == len(buckets[1][dom])
    (DDL_OUT / f"{schema}.sql").write_text(text, encoding="utf-8")
    ddl_stats[dom] = (len(buckets[1][dom]), n_idx,
                      len(buckets[3][dom]), len(buckets[4][dom]))

# ---------------------------------------------------------------- 写数据
data_stats = {}
for dom in DOMAINS:
    schema = SCHEMA[dom]
    order = topo(dom)
    pieces, n_data, n_insert = [], 0, 0
    for t in order:
        if t not in data_blocks:
            pieces.append(f"-- ------ 表 {t}（源导出无数据块：交易流水类按要求仅导 DDL） ------\nCOMMIT;\n")
            continue
        header, body = data_blocks[t]
        body = re.sub(r"(?m)^(\s*INSERT\s+INTO\s+)([A-Z0-9_]+)(\s*\()",
                      lambda m: f'{m.group(1)}"{schema}"."{m.group(2)}"{m.group(3)}', body)
        n = len(insert_re.findall(body))
        assert n == n_insert_src[t], f"{t} INSERT 行数在改写后变化"
        assert not re.search(r'(?m)^\s*INSERT\s+INTO\s+[A-Z0-9_]+\s*\(', body), f"{t} 残留裸表名 INSERT"
        n_insert += n
        n_data += 1 if n else 0
        pieces.append(header + "\n" + body.rstrip() + "\n")
    text = DATA_HEADER.format(schema=schema, pwd=CREDS[schema], n_data=n_data, n_insert=n_insert) + "\n".join(pieces)
    (DATA_OUT / f"{schema}.sql").write_text(text, encoding="utf-8")
    data_stats[dom] = (n_data, n_insert)
assert sum(v[1] for v in data_stats.values()) == sum(
    n for t, n in n_insert_src.items() if t in ALL_DOMAIN), "入域 INSERT 总数不守恒"

# ---------------------------------------------------------------- 03_post_load.sql
post = [
    "-- ============================================================================",
    "-- 灌数后校准脚本（分库后 MUST 执行）",
    "-- 生成：20261112_split_qditp_by_domain.py；来源 identity 列以 01_qditp_ddl.sql 实测为准",
    "-- 执行：四个域各以本域用户执行对应段落，或由 DBA 用有权限账号统一执行",
    "-- 风险：START WITH LIMIT VALUE 需 Oracle 12.2+（红岛/辽阳东预期 19c）；",
    "--       MUST 先在测试库验证语法；不支持时改用各语句上方注释里的 MAX(col)+1 手工填值。",
    "-- ============================================================================",
    "SET DEFINE OFF",
    "WHENEVER SQLERROR CONTINUE",
    "",
]
for dom in DOMAINS:
    schema = SCHEMA[dom]
    rows = [(t, c, k) for t, c, k in identities if ALL_DOMAIN.get(t) == dom]
    post.append(f"-- ==================== {schema} 域（{len(rows)} 个 identity 列） ====================")
    for t, c, k in sorted(rows):
        restore = "ALWAYS" if t in always_tables else k
        post.append(f'-- 校准前核对：SELECT NVL(MAX("{c}"),0)+1 FROM "{schema}"."{t}";')
        post.append(f'ALTER TABLE "{schema}"."{t}" MODIFY ("{c}" GENERATED {restore} '
                    f'AS IDENTITY (START WITH LIMIT VALUE));')
    post.append("")

post += [
    "-- ==================== 校验（执行后 MUST 全绿，NEVER 只看 sqlplus 退出码） ====================",
    "-- 1) 对象数（每域表数见 README；替换为期望值后执行）",
    "SELECT owner, COUNT(*) AS tables_cnt FROM all_tables",
    " WHERE owner IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN') GROUP BY owner ORDER BY owner;",
    "-- 2) 索引 / 约束状态（STATUS 不应有 UNUSABLE，约束应 ENABLED + VALIDATED）",
    "SELECT owner, index_name, status FROM all_indexes",
    " WHERE owner IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN') AND status NOT IN ('VALID','N/A');",
    "SELECT owner, constraint_name, constraint_type, status, validated FROM all_constraints",
    " WHERE owner IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN')",
    "   AND (status <> 'ENABLED' OR validated <> 'VALIDATED');",
    "-- 3) 分区（RANGE 表 MUST 现查 P2027 或已改 INTERVAL；P_MAX 单兜底不等于按月归档可用）",
    "SELECT table_owner, table_name, COUNT(*) AS parts FROM all_tab_partitions",
    " WHERE table_owner IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN')",
    " GROUP BY table_owner, table_name ORDER BY table_owner, table_name;",
    "-- 4) 跨域同义词 / 只读授权（MUST 在服务切 datasource 前落实，否则 ORA-00942）",
    "SELECT owner, synonym_name, table_owner, table_name, status FROM all_synonyms",
    " WHERE owner IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN') ORDER BY owner, synonym_name;",
    "-- 5) 行数比对：源库 QDITP 与目标四域逐表 COUNT(*) 比对（源库地址 MUST 现查），",
    "--    逐表结果 MUST 零差异；不一致即走 README 回滚预案。",
    "COMMIT;",
    "",
]
(OUT / "03_post_load.sql").write_text("\n".join(post), encoding="utf-8")

# ---------------------------------------------------------------- 00_create_users.sql（M1 基建，执行包第 0 步）
# 旧执行包 20261014_bundle_m1_infra.sql 的 Step 0 断言写死红岛容器 HDTPDB / OMF +DATA，
# 换环境必 EXIT；本文件只保留同口径的 Step 1~3（表空间 + 用户 + 基础授权），
# 环境特有值留占位 MUST 现查，跨域 GRANT / 同义词留给建表之后，NEVER 复制旧脚本全量。
TS_SIZE = {"TXN": ("2G", "256M"), "PLAT": ("1G", "128M"),
           "USER": ("512M", "128M"), "RIDE": ("512M", "128M")}
TMP_SIZE = {"TXN": ("512M", "64M"), "PLAT": ("256M", "64M"),
            "USER": ("256M", "64M"), "RIDE": ("256M", "64M")}
m1 = [
    "-- ============================================================================",
    "-- 00 分域账号与表空间（M1 基础建设）—— 分库后执行包第 0 步",
    "-- 来源：docs/ops/environments/scripts/migration/20261014_bundle_m1_infra.sql（红岛 M1 执行包 Step 1~3）",
    "-- 生成：20261112_split_qditp_by_domain.py（口令从唯一真源 docs/ops/environments/跨环境共性.md §三动态解析）",
    "-- 口径：账号命名 / 表空间命名 / 口令与红岛一致（2026-11-12 用户裁定）；口令 NEVER 在本文件硬编码",
    "-- 执行纪律（MUST）：",
    "--   1. SYSDBA 连**目标 PDB**（服务名，NEVER 连 CDB 根）：",
    "--      sqlplus sys/<sys_pwd>@//<目标VIP>:1521/<服务名> as sysdba @00_create_users.sql",
    "--   2. 执行前 MUST 替换占位 `<ASM 磁盘组>`（OMF，如 +DATA）、`<目标PDB>`，并现查磁盘组余量；",
    "--      NEVER 照抄红岛的 +DATA / HDTPDB / 172.120.110.25。",
    "--   3. 生产执行前 MUST 二次确认 + DBA 审批 + 备份；本文件 NEVER 直接执行。",
    "--   4. 口令含 ! / #，CREATE USER 已用双引号定界；SET DEFINE OFF 防 & 被 sqlplus 当替换变量。",
    "--   5. 本文件只建表空间 + 用户 + 基础授权；跨域 GRANT / 同义词 MUST 等 01_ddl/*.sql 建完表后执行",
    "--      （见 20261014_create_split_grants_synonyms.sql，至今仍是草稿、严禁直接在生产执行）。",
    "--      旧脚本的 QDITP_MIGRATOR 角色仅在迁移窗口按需启用，本文件不建。",
    "-- ============================================================================",
    "SET DEFINE OFF",
    "SET VERIFY OFF",
    "WHENEVER SQLERROR CONTINUE",
    "",
    "-- Step 0：现查（NEVER 跳过；旧脚本把红岛值写进 PL/SQL 断言，换环境会直接 EXIT）",
    "-- SELECT sys_context('USERENV','CON_NAME') AS pdb,",
    "--        (SELECT value FROM v$parameter WHERE name='db_create_file_dest') AS omf_dest FROM dual;",
    "-- SELECT name, state, total_mb, free_mb FROM v$asm_diskgroup;  -- 需 >= 4GB 余量（grid 用户 asmcmd lsdg 亦可）",
    "-- 目标容器 MUST = <目标PDB>；OMF 前置 db_create_file_dest MUST = <ASM 磁盘组>。",
    "",
    "-- Step 1：4 个数据表空间（OMF：DATAFILE 只写磁盘组，Oracle 自动落当前 PDB 的 GUID 目录）",
]
for dom in DOMAINS:
    ts = f"ITP_{dom}_TS"
    size, nxt = TS_SIZE[dom]
    m1 += [f"CREATE TABLESPACE {ts}",
           f"  DATAFILE '<ASM 磁盘组>' SIZE {size} AUTOEXTEND ON NEXT {nxt} MAXSIZE UNLIMITED",
           "  ONLINE PERMANENT BLOCKSIZE 8192",
           "  EXTENT MANAGEMENT LOCAL AUTOALLOCATE",
           "  SEGMENT SPACE MANAGEMENT AUTO;", ""]
m1.append("-- Step 2：4 个临时表空间")
for dom in DOMAINS:
    tmp = f"ITP_{dom}_TEMP"
    size, nxt = TMP_SIZE[dom]
    m1 += [f"CREATE TEMPORARY TABLESPACE {tmp}",
           f"  TEMPFILE '<ASM 磁盘组>' SIZE {size} AUTOEXTEND ON NEXT {nxt} MAXSIZE UNLIMITED",
           "  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;", ""]
m1 += ["-- Step 3：4 个域 schema 用户（口令与红岛一致，双引号定界）"]
for dom in DOMAINS:
    s = SCHEMA[dom]
    m1 += [f'CREATE USER {s} IDENTIFIED BY "{CREDS[s]}"',
           f"  DEFAULT TABLESPACE ITP_{dom}_TS",
           f"  TEMPORARY TABLESPACE ITP_{dom}_TEMP;", ""]
m1 += ["GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SYNONYM,",
       "      CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER,",
       "      CREATE MATERIALIZED VIEW, UNLIMITED TABLESPACE",
       "  TO " + ", ".join(SCHEMA[d] for d in DOMAINS) + ";", "",
       "-- Step 4：网关只读账号 itp_ro（共享 USERS + TEMP）",
       f'CREATE USER itp_ro IDENTIFIED BY "{CREDS["itp_ro"]}";',
       "GRANT CREATE SESSION, CREATE SYNONYM TO itp_ro;", "",
       "-- Step 5：自检（MUST 全绿再进入 01_ddl）",
       "SELECT tablespace_name, status, contents FROM dba_tablespaces",
       " WHERE tablespace_name LIKE 'ITP\\_%\\_TS' ESCAPE '\\'",
       "    OR tablespace_name LIKE 'ITP\\_%\\_TEMP' ESCAPE '\\' ORDER BY tablespace_name;",
       "SELECT username, default_tablespace, temporary_tablespace FROM dba_users",
       " WHERE username IN ('QDITP_PLAT','QDITP_USER','QDITP_RIDE','QDITP_TXN','ITP_RO') ORDER BY username;",
       "COMMIT;", "",
]
(OUT / "00_create_users.sql").write_text("\n".join(m1), encoding="utf-8")

# ---------------------------------------------------------------- 未纳入清单（注释态）
ex_lines = [
    "-- ============================================================================",
    "-- 未纳入四域的表（NEVER 直接执行；以下 CREATE TABLE 全部为注释态）",
    "--   BAK_* 16 张 = CTAS 快照，重建不需要；STATION_INFO 1 张 = 不迁，RIDE 改同义词读",
    "--   QDITP_PLAT.TBL_STATION_INFO（见 domain_map.EXCLUDED 与分库计划跨域共享策略）。",
    f"-- 共 {len(ALL_EXCLUDED)} 张；数据行数来自 02_qditp_data.sql，仅计数不复制正文。",
    "-- ============================================================================",
    "",
]
for t in sorted(ALL_EXCLUDED):
    ex_lines.append(f"-- ------ {t}（数据 {n_insert_src.get(t, 0)} 条 INSERT，未纳入） ------")
    for t2, c in excluded_chunks[1]:
        if t2 == t:
            for line in c.strip().splitlines():
                ex_lines.append("-- " + line)
            break
    ex_lines.append("")
for kind, label in ((2, "独立索引"), (3, "表与字段注释"), (4, "约束")):
    ex_lines.append(f"-- ============ 未纳入的{label}（{len(excluded_chunks[kind])} 条，注释态） ============")
    for t, c in excluded_chunks[kind]:
        ex_lines.append(f"-- ------ {t} ------")
        for line in c.strip().splitlines():
            ex_lines.append("-- " + line)
        ex_lines.append("")
(OUT / "_未纳入_BAK快照与STATION_INFO.sql").write_text("\n".join(ex_lines) + "\n", encoding="utf-8")

# 排除项统计（写进 README，供对数）
exc_stat = {k: len(excluded_chunks[k]) for k in (1, 2, 3, 4)}
exc_insert = sum(n_insert_src.get(t, 0) for t in ALL_EXCLUDED)

# ---------------------------------------------------------------- README（统计随重跑刷新）
total_tables = sum(v[0] for v in ddl_stats.values())
readme = f"""# 分库后建表 / 导入脚本（2026-11-12 导出基线）

**输入**：`docs/sql/01_qditp_ddl.sql`（187 张表 / 248 条独立索引 / 1351 条注释 /
12 条约束＝10 条外键 + 2 条 USING INDEX 的 PK/UNIQUE）、
`docs/sql/02_qditp_data.sql`（154 个表块 / 97 张有数据 / 105845 条 INSERT）。
源为测试库 `172.20.222.3:1521/AFCITPDB` schema `QDITP`，**已去除 schema 前缀与 TABLESPACE 子句**。
**生成器**：`docs/ops/environments/scripts/migration/20261112_split_qditp_by_domain.py`（可重跑，带全覆盖断言）。

> 本目录只生成本地文件，**NEVER 连库、NEVER 执行任何 SQL**。
> **2026-11-12 用户裁定：各域账号命名与口令与红岛一致**（见第二节）；
> 目标环境连接串（VIP / 服务名 / 端口）等环境特有值仍 MUST 现查，NEVER 抄红岛 / 测试值。
> 生产执行前 MUST 二次确认 + DBA 审批 + 备份（见 `AGENTS.md` 前置判据）。

## 一、产物

| 文件 | 内容 |
|---|---|
| `00_create_users.sql` | **M1 基建（第 0 步）**：8 个表空间 + 4 域账号 + `itp_ro` + 基础授权；口令与红岛一致，ASM 磁盘组 / PDB 名留现查占位 |
| `01_ddl/QDITP_<域>.sql` × 4 | 分库后建表脚本：建表 → 独立索引 → 注释 → 约束，**全部对象带 `"QDITP_<域>".` 显式前缀** |
| `02_data/QDITP_<域>.sql` × 4 | 分库后导入脚本：`INSERT INTO "QDITP_<域>"."<表>"`，按域内外键拓扑序（父表先入） |
| `03_post_load.sql` | 灌数后校准：全部 identity 列 `START WITH LIMIT VALUE`、2 张恢复 ALWAYS、校验 SQL |
| `_未纳入_BAK快照与STATION_INFO.sql` | 17 张排除表的注释态 DDL（NEVER 直接执行） |

**四域统计**（本次生成实测，重跑会随源刷新）：

| 域 | 表 | 独立索引 | 注释 | 外键约束 | 有数据表 | INSERT 行 |
|---|---|---|---|---|---|---|
"""
for dom in DOMAINS:
    a, b, c, d = ddl_stats[dom]
    e, f = data_stats[dom]
    readme += f"| `QDITP_{dom}` | {a} | {b} | {c} | {d} | {e} | {f} |\n"
readme += f"| **合计** | **{total_tables}** | **{sum(v[1] for v in ddl_stats.values())}** | **{sum(v[2] for v in ddl_stats.values())}** | **{sum(v[3] for v in ddl_stats.values())}** | **{sum(v[0] for v in data_stats.values())}** | **{sum(v[1] for v in data_stats.values())}** |\n"
readme += (f"\n> 守恒口径：**域统计合计 = 源导出 − 排除表**。排除 {len(ALL_EXCLUDED)} 张表带走 "
           f"建表 {exc_stat[1]} / 独立索引 {exc_stat[2]} / 注释 {exc_stat[3]} / 约束 {exc_stat[4]} 条语句"
           f"与 {exc_insert} 条 INSERT，全部在 `_未纳入_BAK快照与STATION_INFO.sql` 留痕（注释态）。\n"
           "> 「约束」列只统计第 4 部分的 10 条外键；另有 2 条 USING INDEX 的 PK / UNIQUE 约束\n"
           "> 内联在 `ALIPAY_SIGN_INFO`(USER) / `GATE_TXN_PAY`(TXN) 的建表块内，随本表一起输出。\n")

readme += f"""
## 二、账号命名与口令（2026-11-12 用户裁定：与红岛一致）

| 域 | schema / 账号 | 口令 | 默认表空间 | 临时表空间 |
|---|---|---|---|---|
"""
for dom in DOMAINS:
    s = SCHEMA[dom]
    readme += f"| {dom} | `{s}` | `{CREDS[s]}` | `ITP_{dom}_TS` | `ITP_{dom}_TEMP` |\n"
readme += f"""| 网关只读 | `itp_ro` | `{CREDS['itp_ro']}` | 共享 USERS + TEMP | — |

> **裁定口径**：四域账号命名（`QDITP_PLAT` / `QDITP_USER` / `QDITP_RIDE` / `QDITP_TXN`）、
> 表空间命名（`ITP_<域>_TS` / `ITP_<域>_TEMP`）与口令**全部与红岛一致**；
> 口令由生成器从唯一真源 `docs/ops/environments/跨环境共性.md`「三、敏感值真源」动态解析，
> **NEVER 在此硬编码**。
> 改密 MUST 同步改该真源表 + `红岛.md` 生产库节 + 各模块 `application.properties` +
> `itpops/cloudos/auth.py` 的 `_PROFILES` + `itpops/web/frontend_release.py` 的 `_profile()` +
> `20261014_bundle_m1_infra.sql`，然后**重跑本生成器**让 8 个脚本头与本节同步。
> 口令含 `!` / `#`，sqlplus 命令行 MUST 用单引号包裹（防 shell 历史展开），
> 或改走 `CONNECT <用户>/"<口令>"@//<VIP>:1521/<服务名>` 双引号定界。
>
> `00_create_users.sql` 的 5 个 `CREATE USER` 复用同一批口令（同样从真源动态解析），NEVER 另设口令。
>
> **仍 MUST 现查**：DB VIP / 服务名 / 端口 / NodePort / 公网入口 / HSM 等环境特有值，
> **NEVER 抄红岛 / 测试值**；本裁定只覆盖「账号命名 + 口令」两项。

"""
readme += """
## 三、域映射与本次新增裁决

基线真源是 `docs/ops/environments/scripts/migration/domain_map.py`（2026-10-23 四域清单，175 张）。
**本次导出多 12 张表**，增量映射记在生成器里，**NEVER 直接改 `domain_map.py`**——
10-23 两个拆分脚本断言「映射 == 175 对象全覆盖」，加表会让它们重跑失败。

| 新增表 | 归属 | 依据 | 状态 |
|---|---|---|---|
| `ASYNC_EXPORT_TASK` | PLAT | web-admin 异步导出（`AsyncExportQuartzTask` / `async-export-mvp.sql`） | 按代码归属推定，**MUST 经 DBA / 业务确认** |
| `GRAYLIST` / `GRAYLIST_OPERATE_LOG` | PLAT | blacklist-server（`GraylistService` / `graylist-schema.sql`） | 同上 |
| `HCE_BLACKLIST_FILE_STATE` | PLAT | blacklist-server HCE 黑名单文件版本状态表 | 同上 |
| `RECON_UPLOAD_FILE` | PLAT | **web-server 综管台**对账上传记录（`recon-upload-file-menu.sql`），与 recon-server 的 `RECON_*` 无外键 | 同上；名字带 RECON 易误判归 TXN，MUST 确认 |
| `TBL_DEVICE_INFO` | PLAT | para-server（`DeviceInfoServiceImpl` / `para-schema.sql`）；10-23 版曾是「本地有 DDL、老库导出无此表」的 UNKNOWN 桶，本次老库导出已证实存在 | 同上 |
| `BAK_SYS_*_20261104/20261106` 6 张 | **排除** | CTAS 快照，重建不需要 | 与旧 `BAK_*` 同口径 |

> 确认后 MUST 回写：`domain_map.py` 的四域清单 / `EXCLUDED`、`docs/ops/数据库按域分库计划.md`「3. 表归属清单」节、
> `deploy/sche/by-domain/README.md` 的映射总表，并重跑 10-23 两个拆分脚本核对（届时需同步放宽其 175 断言）。

**排除表 17 张**：`BAK_*` 16 张 CTAS 快照 + `STATION_INFO`（不迁，RIDE 改同义词读
`QDITP_PLAT.TBL_STATION_INFO`）。数据文件里这些表仍有 INSERT，本产物**不导入**。

## 四、执行顺序（本机不执行，供 DBA 走审批）

1. **M1 基础设施**：执行 `00_create_users.sql`（8 个表空间 + 4 域账号 + `itp_ro` + 基础授权；
   结构口径同 `docs/ops/environments/scripts/migration/20261014_bundle_m1_infra.sql` Step 1~3）。
   **`<ASM 磁盘组>` / `<目标PDB>` / 连接串 MUST 现查替换**，NEVER 照抄红岛的 `+DATA` / `HDTPDB` /
   `172.120.110.25`——旧脚本 Step 0 断言把这三个值写死，换环境会直接 EXIT。
   **账号命名与口令按第二节裁定与红岛一致，NEVER 改**。
2. **建表**：按 `PLAT → USER → RIDE → TXN` 执行 `01_ddl/QDITP_<域>.sql`，各域**以本域用户连接**：
   `sqlplus 'QDITP_<域>/<第二节对应口令>@//<目标VIP>:1521/<服务名>' @QDITP_<域>.sql`
   （单引号 MUST 保留：口令含 `!` / `#`；NEVER 用 SYS）。
3. **跨域同义词 / 只读授权**：`docs/ops/environments/scripts/migration/20261014_create_split_grants_synonyms.sql`
   至今仍是草稿、文件头写明严禁直接在生产执行；服务切 datasource 前 MUST 落实，否则跨域读
   PLAT 表（如 `TBL_STATION_INFO`）直接 `ORA-00942`。
4. **导数据**：按域执行 `02_data/QDITP_<域>.sql`；TXN 域灌数期 MUST 停 recon / face-pay / web-admin
   定时任务（双跑红线，见 `docs/ops/K8S迁移清单.md`「R5 切换方式」）。
5. **灌数后校准**：执行 `03_post_load.sql`（identity 列校准 + 2 张恢复 ALWAYS + 校验 SQL）。
6. **校验与切流**：逐表行数零差异、索引无 UNUSABLE、约束全 VALIDATED、分区合规、同义词可解析；
   再切各模块 datasource、观察 30 分钟 + 跑一笔真实交易，观察期 7 天。
7. **回滚**：4 级递进（撤授权 → 收角色 → 切回原 datasource → 兜底删 schema），
   见 `docs/ops/environments/scripts/migration/20261014_rollback_split.sql`。

## 五、必须知道的坑（红岛 2026-10-23 实测 + 本次导出新发现）

1. **2 张 `GENERATED ALWAYS AS IDENTITY` 带显式 ID**：`APP_PAY_SIGN_REQUEST.ID`（TXN，210 行）、
   `GATEWAY_ROUTE_CONFIG_AUDIT.AUDIT_ID`（PLAT，18 行）。直接灌数会 **ORA-32795** ⇒ 建表脚本里
   这两张已临时降为 `BY DEFAULT`，灌完 MUST 由 `03_post_load.sql` 恢复 ALWAYS。
2. **identity 列不校准 = 后台首次新增即主键冲突**：本次导出共
"""
readme += f"**{len(identities)} 个 identity 列**，导完一律 `START WITH LIMIT VALUE`（红岛踩过）。\n"
readme += """3. **虚拟列不能 INSERT（ORA-54013）**：`QRCODE_STATUS.CARD_ID_TAIL`、
   `USER_ITP_REG_INFO.THIRD_USER_ID_SUFFIX`；源导出已自动把它们排除在 INSERT 列清单外，NEVER 手工补回。
4. **外键零跨域**（12 条约束全部域内），但**数据顺序仍要拓扑**：字母序会让 `QRTZ_BLOB_TRIGGERS`
   先于父表 `QRTZ_TRIGGERS` 插入 → FK 违例；生成器已按域内 FK 拓扑排序。
5. **独立索引这次是齐的**：源导出共 248 条（246 条在第 2 部分 + 2 条内联在
   `ALIPAY_SIGN_INFO` / `GATE_TXN_PAY` 的建表块，其中 82 条 UNIQUE），不像 10-23 版缺 169 个；
   那 2 张表的 `USING INDEX` 约束引用同块建立的索引，故四部分顺序 NEVER 调换。
   兼容性兜底：若目标库对 `USING INDEX "QDITP_<域>"."<索引>"` 报 ORA-00903 / ORA-01418，
   把该处 schema 前缀去掉即可（会话已设 `CURRENT_SCHEMA`，裸索引名可解析到本域）。
6. **分区**：源导出有 13 处 `PARTITION BY`；RANGE 表老库实况是 `P202606~P202612 + P_MAX`，
   2027-01-01 起数据全落 `P_MAX`、按月归档失效 ⇒ **2026-12-15 前**补月度分区或改 INTERVAL。
7. **字符集 / 客户端**：源文件 UTF-8；目标库 AL32UTF8 直接用 UTF-8 客户端，ZHS16GBK 由驱动转码
   （见 `01_qditp_ddl.sql` 头部）。DDL 带 `COLLATE "USING_NLS_COMP"`，目标库须为 12.2+。
8. **`WHENEVER SQLERROR CONTINUE` 会静默吞错**（红岛首轮 102/102 全失败才发现）：
   执行后 MUST 跑 `03_post_load.sql` 的校验 SQL + 逐表行数比对，NEVER 只看 sqlplus 退出码。
9. **幂等**：重复建表报 ORA-00955、重复导入报主键/唯一键冲突；重导前 MUST TRUNCATE 或换库。

## 六、与 2026-10-23 红岛版（`deploy/sche/by-domain/`）的关系

两版并存、**NEVER 互相覆盖**：那版源为 10-23 老库导出（175 对象、带 `"QDITP".` 前缀、缺独立索引），
表结构防老库漂移、索引靠本地 DDL 补；本版源为 11-12 新导出（187 表、已去前缀、含全部独立索引），
**对齐老库当前实况**。红岛四域已于 2026-10-23 建完（164 表 / 362 索引 / 341.2 万行），
本产物用于新环境（如辽阳东）重建或重跑演练，**NEVER 直接对红岛生产库执行**。

## 七、NEVER 清单

- NEVER 把本目录脚本直接对生产库执行（MUST DBA 审批 + 二次确认 + 备份 + 测试库演练）；
- NEVER 抄红岛 / 测试的**连接串（VIP / 服务名 / NodePort / 公网入口 / HSM 地址）**到目标环境；
  账号命名与口令按 2026-11-12 裁定与红岛一致（见第二节），口令真源见
  `docs/ops/environments/跨环境共性.md`「三、敏感值真源」，改密 MUST 重跑生成器；
- NEVER 用 SYS 或其它域账号执行域脚本；NEVER 调换文件内四部分顺序；
- NEVER 手改生成物（改口径请改生成器后重跑，保证断言与 README 统计同步）。
"""
(OUT / "README.md").write_text(readme, encoding="utf-8")

# ---------------------------------------------------------------- 输出统计
print("生成完成：", OUT)
print("  00_create_users.sql: 8 表空间 / 5 用户（口令与红岛一致，ASM 磁盘组与 PDB 名留现查占位）")
for dom in DOMAINS:
    a, b, c, d = ddl_stats[dom]
    e, f = data_stats[dom]
    print(f"  QDITP_{dom}: 表 {a} / 索引 {b} / 注释 {c} / 约束 {d} / 有数据表 {e} / INSERT {f}")
print(f"  合计：表 {total_tables}（排除 {len(ALL_EXCLUDED)}）/ identity 列 {len(identities)} "
      f"/ ALWAYS 降级 {len(always_tables)} / 入域 INSERT {sum(v[1] for v in data_stats.values())}")
print("  自检：语句数守恒、域前缀隔离、外键零跨域、INSERT 行数守恒 —— 全部通过")
