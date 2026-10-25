#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""连老库 AFCITPDB 核对 _补充索引_本地DDL_需与老库核对.sql 里的候选索引。

背景：deploy/sche/sche.sql（老库导出）只含「表 + 内联约束 + 约束背书索引」，
deploy/sche/by-domain/_补充索引_本地DDL_需与老库核对.sql 的 169 个独立索引全部
来自仓库本地 DDL，NEVER 当作老库已核实的事实——本脚本就是补这一核。

连上时（MUST）：
  逐索引比对 all_indexes / all_ind_columns / all_part_indexes，判定：
    ✅ 老库已存在且唯一性/列/分区类型一致
    ⚠️ 老库已存在但不一致（列漂移/唯一性/分区索引类型/状态非 VALID）
    ❌ 老库不存在（疑本地 DDL 幻影或已删，执行前 MUST 人工裁决）
  反向：老库有、本地清单与老库导出都没有的索引 → 打印出来（红岛 scheme 必须补）
  核对结论按条目回写进 _补充索引_本地DDL_需与老库核对.sql（原文语句不动）。

连不上时：只打印网络原因与解法，exit 2，NEVER 改任何文件。

连接参数：默认 qditp/qditp@172.20.222.3:1521/AFCITPDB（测试环境，见工作记忆）；
口令可用环境变量 ITP_DB_PASSWORD 覆盖。
"""
import os
import re
import sys
from datetime import datetime
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
IDX_FILE = REPO / "deploy/sche/by-domain/_补充索引_本地DDL_需与老库核对.sql"
EXPORT_FILE = REPO / "deploy/sche/sche.sql"

DSN = os.environ.get("ITP_DB_DSN", "172.20.222.3:1521/AFCITPDB")
USER = os.environ.get("ITP_DB_USER", "qditp")
PASSWORD = os.environ.get("ITP_DB_PASSWORD", "qditp")


def norm_cols(s: str) -> list:
    """归一化列清单：去引号/大写/去 ASC-DESC 方向后缀（all_ind_columns 不含方向）"""
    out = []
    for c in s.split(","):
        toks = c.strip().strip('"').upper().split()
        if toks:
            out.append(toks[0])
    return out


# ---------------------------------------------------------------- 解析候选清单
def parse_candidates():
    """从补充索引文件解析候选：[(name, table, unique, domain, cols, part, verdict_anchor)]"""
    text = IDX_FILE.read_text(encoding="utf-8")
    lines = text.splitlines()
    out = []
    i = 0
    cmt_re = re.compile(r"^-- (\w+) \[(UNIQUE|INDEX)\] ON (\w+)\s+-- 来源 (.+)$")
    stmt_re = re.compile(
        r'^CREATE (UNIQUE )?INDEX (?:"(\w+)"\.)?"?(\w+)"? ON (?:"QDITP_(\w+)"\."(\w+)"|(\w+)) \((.*)\)(?: (GLOBAL|LOCAL))?;$',
        re.I)
    while i < len(lines):
        m = cmt_re.match(lines[i])
        if m and i + 1 < len(lines):
            sm = None
            for j in range(i + 1, min(i + 4, len(lines))):
                sm = stmt_re.match(lines[j])
                if sm:
                    break
            if sm:
                name, uniq, table = m.group(1), m.group(2) == "UNIQUE", m.group(3)
                raw_cols = sm.group(7)                       # 组7=列清单（ON 分支无论限定与否都走这组）
                part = (sm.group(8) or "").upper() or None
                # 函数式索引（CASE/WHEN/NVL/DECODE 表达式）：all_ind_columns 无可比列，只验存在性
                fbi = bool(re.search(r"\bCASE\b|\bWHEN\b|\bNVL\b|\bDECODE\b|\bCOALESCE\b", raw_cols, re.I))
                cols = None if fbi else norm_cols(raw_cols)
                out.append({
                    "name": name, "table": table, "unique": uniq, "fbi": fbi,
                    "domain": sm.group(4) or "UNKNOWN", "cols": cols, "part": part,
                    "line_idx": i,
                })
                i = j + 1
                continue
        i += 1
    return lines, out


# ---------------------------------------------------------------- 连库
def connect():
    try:
        import oracledb
    except ImportError:
        print("[FAIL] 本机没有 oracledb 模块（pip install oracledb）", file=sys.stderr)
        sys.exit(2)
    try:
        return oracledb.connect(user=USER, password=PASSWORD, dsn=DSN)
    except Exception as e:
        print(f"[FAIL] 连不上老库 {USER}@{DSN}：{type(e).__name__}: {e}")
        print("       解法（三选一）：① 换到能通达 172.20.222.3 的公司网络/堡垒机再跑；"
              "② 让集群内可达网段的同事执行本脚本；③ 手动执行核对 SQL（见脚本头注释）")
        sys.exit(2)


def fetch_db_indexes(conn):
    cur = conn.cursor()
    idx = {}
    for name, table, uniq, status, itype, part_flag in cur.execute(
            "SELECT index_name, table_name, uniqueness, status, index_type, partitioned "
            "FROM all_indexes WHERE owner = 'QDITP'"):
        idx[name] = {"table": table, "unique": uniq == "UNIQUE", "status": status,
                     "itype": itype, "cols": [], "part": None}
    for name, col, pos in cur.execute(
            "SELECT ic.index_name, ic.column_name, ic.column_position "
            "FROM all_ind_columns ic JOIN all_indexes i "
            "  ON i.owner = ic.index_owner AND i.index_name = ic.index_name "
            "WHERE i.owner = 'QDITP' ORDER BY ic.index_name, ic.column_position"):
        if name in idx:
            idx[name]["cols"].append(col)
    for name, ptype in cur.execute(
            "SELECT index_name, partitioning_type FROM all_part_indexes WHERE owner = 'QDITP'"):
        if name in idx:
            idx[name]["part"] = ptype
    return idx


def export_known_names():
    """老库导出里已有的索引/约束名（约束背书，不在待补清单里属正常）"""
    text = EXPORT_FILE.read_text(encoding="utf-8", errors="replace")
    names = set(re.findall(r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+"QDITP"\."(\w+)"', text, re.I))
    names |= set(re.findall(r'CONSTRAINT\s+"(\w+)"', text))
    return names


# ---------------------------------------------------------------- 主流程
def main():
    lines, cands = parse_candidates()
    print(f"候选索引 {len(cands)} 个（来自 {IDX_FILE.name}）")
    conn = connect()
    db = fetch_db_indexes(conn)
    conn.close()
    print(f"老库 all_indexes（owner=QDITP）：{len(db)} 个索引\n")

    verdicts = {}   # name -> (emoji, 详情)
    for c in cands:
        d = db.get(c["name"])
        if d is None:
            verdicts[c["name"]] = ("❌", "老库不存在（疑本地 DDL 幻影/已删，执行前 MUST 人工裁决）")
            continue
        problems = []
        if d["unique"] != c["unique"]:
            problems.append(f"唯一性不一致：老库={'UNIQUE' if d['unique'] else 'NONUNIQUE'} vs 本地={'UNIQUE' if c['unique'] else 'INDEX'}")
        if c.get("fbi"):
            pass  # 函数式索引：all_ind_columns 无比对列，只验存在性/唯一性/状态
        elif d["cols"] != c["cols"]:
            problems.append(f"列不一致：老库({','.join(d['cols'])}) vs 本地({','.join(c['cols'])})")
        if d["table"] != c["table"]:
            problems.append(f"表不一致：老库 {d['table']} vs 本地 {c['table']}")
        if c["part"] and d["part"] and c["part"] != d["part"]:
            problems.append(f"分区索引类型不一致：老库 {d['part']} vs 本地 {c['part']}")
        if d["status"] != "VALID":
            problems.append(f"老库索引状态 {d['status']}（非 VALID，重建前 MUST 关注）")
        if problems:
            verdicts[c["name"]] = ("⚠️", "；".join(problems))
        elif c.get("fbi"):
            verdicts[c["name"]] = ("✅", "老库已存在（函数式索引，未逐列比对）")
        else:
            verdicts[c["name"]] = ("✅", "老库已存在且一致")

    # 反向：老库有、本地清单没有、且不在老库导出（约束背书）里的索引
    known = export_known_names()
    cand_names = {c["name"] for c in cands}
    db_only = sorted(n for n in db if n not in cand_names and n not in known)

    # ---------------------------------------------------------------- 报告
    ok = sum(1 for v in verdicts.values() if v[0] == "✅")
    warn = sum(1 for v in verdicts.values() if v[0] == "⚠️")
    bad = sum(1 for v in verdicts.values() if v[0] == "❌")
    print(f"核对结论：✅ {ok} 个一致 | ⚠️ {warn} 个不一致 | ❌ {bad} 个老库不存在")
    for c in cands:
        if verdicts[c["name"]][0] != "✅":
            print(f"  {verdicts[c['name']][0]} {c['name']} (ON {c['table']}, {c['domain']}): {verdicts[c['name']][1]}")
    if db_only:
        print(f"\n⚠️ 老库有、本地清单与老库导出都没有的索引 {len(db_only)} 个（红岛 scheme MUST 补，先查它们是否约束背书）：")
        for n in db_only:
            d = db[n]
            print(f"  {n} ON {d['table']} ({','.join(d['cols'])}){' UNIQUE' if d['unique'] else ''}")

    # ---------------------------------------------------------------- 回写补充文件
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    # 按原始行号倒序插核对结论（避免位移）
    inserts = []
    for c in cands:
        emoji, detail = verdicts[c["name"]]
        inserts.append((c["line_idx"] + 1, f"-- 核对@{now}: {emoji} {detail}"))
    for pos, txt in sorted(inserts, reverse=True):
        lines.insert(pos, txt)

    summary = [
        f"-- ==================== 核对结论（{now}，对老库 {DSN}） ====================",
        f"-- ✅ {ok} 个一致 | ⚠️ {warn} 个不一致 | ❌ {bad} 个老库不存在 | 反向缺口（老库有本地无）{len(db_only)} 个",
        "-- 生成方式：python3 docs/ops/environments/scripts/migration/20261023_verify_indexes_against_db.py（可重跑刷新）",
        "-- ==================================================================================",
        "",
    ]
    # 插到文件头注释块之后（第一个域分节之前）
    anchor = next((i for i, ln in enumerate(lines) if ln.startswith("-- ==================== QDITP_")), None)
    if anchor is None:
        anchor = 0
    lines[anchor:anchor] = summary
    IDX_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"\n[OK] 核对结论已回写 {IDX_FILE}")
    return 0 if (warn == 0 and bad == 0 and not db_only) else 1


if __name__ == "__main__":
    sys.exit(main())
