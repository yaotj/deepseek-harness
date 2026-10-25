#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 deploy/data/*.sql（2026-10-23 老库数据导出）按域拆成 4 个导入文件。

与 20261023_split_sche_by_domain.py 共用 domain_map.py 的同一套域映射。

数据格式（实测）：纯 `INSERT INTO QDITP.<表> (列) VALUES` 批插语句，值行以 TAB 缩进，
无 COMMIT / SET / ALTER；96 张表为空文件（0 行）。表前缀未加引号。

变换：
  - 只改「行首 INSERT INTO QDITP.<表>」的前缀 → `INSERT INTO QDITP_<域>.<表>`
    （NEVER 全文替换 "QDITP."——JSON 报文体里可能出现该字样，会污染数据）
  - 其余字节原样透传（encoding=utf-8 + surrogateescape，保证逐字节保真）
  - 表顺序：域内按外键拓扑排序（父表先于子表），自引用与环回退字母序并告警

排除：BAK_* 10 张快照表 + STATION_INFO（与 scheme 拆分同口径，见 domain_map.EXCLUDED）
输出：deploy/data/by-domain/QDITP_<域>.sql + README.md
"""
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from domain_map import DOMAIN, EXCLUDED, DOMAINS  # noqa: E402

# 仓库根：本文件位于 <repo>/docs/ops/environments/scripts/migration/，需上溯 5 层。
# 旧值 parents[2] 解析到 docs/ops/environments，其下没有 deploy/（实测 False），是 off-by-3。
REPO = Path(__file__).resolve().parents[5]
DATA = REPO / "deploy/data"
SCHEME_DIR = REPO / "deploy/sche/by-domain"
OUT = REPO / "deploy/data/by-domain"
OUT.mkdir(parents=True, exist_ok=True)

FILE_HEADER = """-- ============================================================================
-- {schema} 域数据导入文件（{n_tables} 张表，{n_data} 张有数据）
-- 来源：deploy/data/*.sql（2026-10-23 08:27 老库 QDITP 数据导出，共 175 个文件）
-- 生成：docs/ops/environments/scripts/migration/20261023_split_data_by_domain.py（与 scheme 拆分共用 domain_map.py）
-- 执行纪律（MUST）：
--   1. MUST 先建好本域表结构（deploy/sche/by-domain/{schema}.sql）与索引
--      （_补充索引_本地DDL_需与老库核对.sql 核对后执行）再导数据
--   2. 以域用户执行：sqlplus {schema}/<pwd>@//<红岛PDB> @{schema}.sql
--      NEVER 用 SYS 执行（owner 错且落 SYSTEM 表空间）
--   3. 建议会话开关：SET DEFINE OFF（防数据里的 & 被 sqlplus 当替换变量）、
--      SET NLS_LANG 与导出一致、SET AUTOCOMMIT ON 或分段执行（导出无 COMMIT，
--      单文件超大事务注意 undo）
--   4. 表顺序已按域内外键拓扑排序（父表先入）；若仍报 FK 违例（存量脏数据），
--      先 DISABLE 约束 → 导完 → ENABLE VALIDATE
--   5. 幂等：重复导入会主键/唯一键冲突；重导前 MUST 清表（TRUNCATE）或换环境
--   6. BAK_* 快照表与 STATION_INFO 不在任何域文件内（与 scheme 拆分同口径）
-- ============================================================================
"""

# ---------------------------------------------------------------- 数据文件 → 表
data_files = {}   # table -> path
for p in sorted(DATA.glob("*.sql")):
    m = re.match(r"^(.+)_(\d{12})\.sql$", p.name)
    assert m, f"数据文件名不符合 <表>_<YYYYMMDDHHMM>.sql: {p.name}"
    table = m.group(1)
    assert table not in data_files, f"同一张表有多个数据文件: {table}"
    data_files[table] = p

mapped = set(DOMAIN) | set(EXCLUDED)
assert set(data_files) == mapped, (
    f"数据文件与映射不一致：多 {sorted(set(data_files) - mapped)}，少 {sorted(mapped - set(data_files))}")
print(f"数据文件 {len(data_files)} 个（入域 {sum(1 for t in data_files if t in DOMAIN)}，排除 {sum(1 for t in data_files if t in EXCLUDED)}）")

# ---------------------------------------------------------------- 域内外键拓扑序
def fk_order(dom: str):
    """从域 scheme 文件解析外键，返回 (拓扑序表列表, 环内告警列表)"""
    scheme = (SCHEME_DIR / f"QDITP_{dom}.sql").read_text(encoding="utf-8", errors="replace")
    tables = {t for t, d in DOMAIN.items() if d == dom}
    parents = {t: set() for t in tables}     # 子表 -> 父表集合
    # 逐约束块建边：CONSTRAINT "x" FOREIGN KEY (cols) REFERENCES ".."."PARENT"
    for m in re.finditer(r'CONSTRAINT "\w+" FOREIGN KEY \([^)]*\)\s*REFERENCES "QDITP_\w+"\."(\w+)"', scheme):
        parent = m.group(1)
        # 找该约束所属表：向前最近的 CREATE TABLE "QDITP_X"."TABLE"
        cm = re.findall(r'CREATE TABLE "QDITP_\w+"\."(\w+)"', scheme[:m.start()])
        child = cm[-1] if cm else None
        if child and parent in tables and child in tables and child != parent:
            parents[child].add(parent)
        # 自引用外键（child == parent）忽略
    order, done, cycle = [], set(), []
    remaining = sorted(tables)
    while remaining:
        ready = [t for t in remaining if parents[t] <= done]
        if not ready:                       # 环：按字母序吞一批并告警
            cycle.extend(remaining)
            order.extend(remaining)
            break
        for t in ready:
            order.append(t)
            done.add(t)
        remaining = [t for t in remaining if t not in done]
    return order, cycle

# ---------------------------------------------------------------- 切分
PREFIX_RE = re.compile(r"^INSERT INTO (QDITP\.)")
QUOTED_PREFIX_RE = re.compile(r'^INSERT INTO "QDITP"\.')

summary = {}
t0 = time.time()
for dom in DOMAINS:
    order, cycle = fk_order(dom)
    assert set(order) == {t for t, d in DOMAIN.items() if d == dom}
    tables = sorted(t for t, d in DOMAIN.items() if d == dom)
    n_data = sum(1 for t in tables if data_files[t].stat().st_size > 0)
    out_path = OUT / f"QDITP_{dom}.sql"
    total_ins = 0
    with open(out_path, "w", encoding="utf-8") as w:
        w.write(FILE_HEADER.format(schema=f"QDITP_{dom}", n_tables=len(tables), n_data=n_data))
        if cycle:
            w.write(f"-- ⚠️ 外键环（字母序回退，导数据若报 FK 请先 DISABLE 约束）: {', '.join(cycle)}\n")
        w.write("\n")
        for t in order:
            src = data_files[t]
            size = src.stat().st_size
            if size == 0:
                w.write(f"-- {t}: 0 行（源文件为空）\n\n")
                summary[t] = 0
                continue
            n = 0
            w.write(f"-- ==================== {t}（源 {src.name}） ====================\n")
            with open(src, "r", encoding="utf-8", errors="surrogateescape", newline="") as r:
                for line in r:
                    if line.startswith("INSERT INTO QDITP."):
                        w.write(f"INSERT INTO QDITP_{dom}." + line[len("INSERT INTO QDITP."):])
                        n += 1
                    elif QUOTED_PREFIX_RE.match(line):
                        w.write(line.replace('INSERT INTO "QDITP".', f'INSERT INTO "QDITP_{dom}".', 1))
                        n += 1
                    else:
                        w.write(line)
            total_ins += n
            summary[t] = n
            w.write("\n")
            print(f"  [QDITP_{dom}] {t:36s} {n:>7d} 条 INSERT")
    print(f"[OK] {out_path.name}: {len(tables)} 张表（{n_data} 张有数据），{total_ins} 条 INSERT，"
          f"{out_path.stat().st_size/1048576:.1f} MB，耗时 {time.time()-t0:.0f}s")

# ---------------------------------------------------------------- 总量校验
# 只数入域表的源 INSERT（排除表 BAK_*/STATION_INFO 不进任何域文件，不参与守恒）
src_total = 0
for t, p in data_files.items():
    if t not in DOMAIN:
        continue
    with open(p, "r", encoding="utf-8", errors="surrogateescape") as r:
        src_total += sum(1 for ln in r if ln.startswith("INSERT INTO QDITP."))
out_total = sum(summary.values())
print(f"\nINSERT 语句守恒（入域 164 表）：源 {src_total} = 拆分后 {out_total}")
assert src_total == out_total, "INSERT 条数不守恒，切分过程丢内容！"

# ---------------------------------------------------------------- README
rows = []
for dom in DOMAINS:
    tables = sorted(t for t, d in DOMAIN.items() if d == dom)
    has = [t for t in tables if data_files[t].stat().st_size > 0]
    empty = [t for t in tables if data_files[t].stat().st_size == 0]
    rows.append(f"| `QDITP_{dom}.sql` | QDITP_{dom} | {len(tables)} | {len(has)} | {sum(summary[t] for t in tables)} |")
empty_all = sorted(t for t in DOMAIN if data_files[t].stat().st_size == 0)
excl_rows = "\n".join(f"| {t} | {'CTAS 快照，重建不需要' if t != 'STATION_INFO' else '不迁（同义词方案）'} |" for t in sorted(EXCLUDED))
readme = f"""# 老库导出数据按域拆分（2026-10-23）

**源**：`deploy/data/` 175 个 `<表>_<日期>.sql`（2026-10-23 08:27 老库导出，共 1.0G；{len(empty_all)} 张表 0 行）。
**生成器**：`docs/ops/environments/scripts/migration/20261023_split_data_by_domain.py`（与 scheme 拆分共用 `domain_map.py` 同一套域映射；INSERT 条数守恒已断言）。
**配套**：表结构用 `deploy/sche/by-domain/`，索引用 `_补充索引_本地DDL_需与老库核对.sql`（连库核对后执行）。

## 文件与执行顺序

| 文件 | 目标 schema | 表数 | 有数据 | INSERT 条数 |
|---|---|---|---|---|
{chr(10).join(rows)}

- 顺序 MUST：PLAT → USER → RIDE → TXN（与建表同序；各域内部已按外键拓扑排序，父表先入）
- 只改行首 `INSERT INTO QDITP.<表>` 前缀为 `INSERT INTO QDITP_<域>.<表>`；值行原样透传（`utf-8 + surrogateescape` 逐字节保真，NEVER 全文替换 `QDITP.`——JSON 报文体里会出现该字样）
- 执行纪律见各文件头（SET DEFINE OFF / 以域用户执行 / FK 失败时先 DISABLE 后 ENABLE VALIDATE / 无 COMMIT 注意事务大小）

## 空表（{len(empty_all)} 张，源文件 0 字节，域文件里只有注释行）

{', '.join(empty_all)}

## 不纳入（11 张，与 scheme 拆分同口径）

| 表 | 原因 |
|---|---|
{excl_rows}

## 校验

- 数据文件表名集合 == 域映射入域 164 + 排除 11（断言，漏一张即报错）
- INSERT 条数守恒：源 {src_total} == 拆分后 {out_total}（断言）
- 分区表（GATE_TXN_PAY / QRCODE_TXN_DETAIL / ALIPAY_PAY_* / F2F_ORDER / F2F_PAYMENT / F2F_RESULT_REPORT / PAY_TXN_DETAIL 等）数据原样导入；这些表在老库有 `P_MAX` 兜底分区，2027 起数据落 P_MAX——按月归档失效问题见 scheme README §2.6
"""
(OUT / "README.md").write_text(readme, encoding="utf-8")
print(f"[OK] README.md")
