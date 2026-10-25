#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 deploy/sche/sche.sql（2026-10-23 老库 QDITP 全量导出）按域拆成 4 个建表脚本。

域归属真源（按优先级）：
  1. docs/ops/environments/scripts/migration/20261017_schema_manifest.md 的四域清单（bundle IN 列表交叉校验）
     + 2026-10-19 补录的 5 张 RuoYi 关联表（SYS_JOB_LOG / SYS_ROLE_DEPT / SYS_ROLE_MENU /
       SYS_USER_POST / SYS_USER_ROLE → PLAT）
     + manifest「缺口与未分域」裁决（AGM_DEVICE_STATUS / GATEWAY_ROUTE_CONFIG /
       GATEWAY_ROUTE_CONFIG_AUDIT / QRTZ_SIMPROP_TRIGGERS → PLAT）
  2. docs/ops/数据库按域分库计划.md §3 勘误（2026-10-14）：
     - alipay-account 域 8 张 → QDITP_USER
     - DISCOUNT_LEVEL / METRO_TRANSFER_PUSH_TASK（gate-txn-pay 拥有）→ QDITP_TXN
     - APP_PAY_LOGS（collect-pay 旧表、gate-txn-pay 读，计划建议 TXN）→ QDITP_TXN
     - TVM/BOM 购票域 18 张 + 零散 4 张（TBL_APP_ORDER_REFUND / TBL_TICKET_REFUND_RECORD /
       TBL_NOTICE_APP_REFUND_RECORD / TBL_NOTICE_APP_TAKETICKET_RECORD）：
       计划建议「PLAT 或按 tvm-bom-pay 独断」，本脚本取 PLAT（collect-pay 2026-10-13 已下线、
       旧表无新数据；PLAT 已托管 ACC 设备与 NOTICE 同类表）——【待用户确认，见 README §4】
  3. BAK_*（10 张 CTAS 快照）与 STATION_INFO（manifest 裁决不迁，RIDE 改同义词读
     PLAT.TBL_STATION_INFO）不纳入四域脚本，进 _未纳入.sql（语句整段注释保留）。

变换规则：
  - "QDITP".<对象> → "QDITP_<域>".<对象>（CREATE TABLE / COMMENT ON / CREATE INDEX /
    ALTER TABLE 全部改写）
  - 删除全部 TABLESPACE "USERS" 子句（红岛以域用户执行、落 M1 绑定的 ITP_<域>_TS 默认表空间；
    与 20261017_schema_*.sql「无 TABLESPACE 子句」的纪律一致）
  - COMMENT ON 全部保留（老库注释是资产）
  - 源文件 CRLF，读出时统一为 LF（Python 文本模式），sqlplus 兼容无差异

已知缺口（README §6 有完整清单）：老库导出只含「表 + 内联约束 + 约束背书索引」，
本地 DDL 中的 172 个独立索引有 171 个不在导出里（含 34 个唯一索引/幂等键）——
本脚本额外生成 _补充索引_本地DDL_需与老库核对.sql（来源本地 DDL，非老库导出）。
"""
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
SRC = REPO / "deploy/sche/sche.sql"
OUT = REPO / "deploy/sche/by-domain"
OUT.mkdir(parents=True, exist_ok=True)

# ---------------------------------------------------------------- 域映射
# 唯一真源 = docs/ops/environments/scripts/migration/20261023_domain_map.py（scheme / data 两个拆分脚本共用）
from domain_map import DOMAIN, EXCLUDED, source_of  # noqa: E402

# ---------------------------------------------------------------- 解析导出
text = SRC.read_text(encoding="utf-8")
parts = re.split(r"(?m)^(-- QDITP\.([A-Z0-9_$]+) definition)[ \t]*$", text)
assert parts[0].strip() == "", "导出文件开头不是分节注释，格式与预期不符"

sections = []  # (name, header, body)
for i in range(1, len(parts), 3):
    header, name, body = parts[i], parts[i + 1], parts[i + 2]
    sections.append((name, header, body))

names = [n for n, _, _ in sections]
assert len(names) == len(set(names)), f"导出中存在重名对象: {sorted(n for n in set(names) if names.count(n) > 1)}"
missing = sorted(set(names) - set(DOMAIN) - set(EXCLUDED))
extra = sorted((set(DOMAIN) | set(EXCLUDED)) - set(names))
assert not missing, f"映射表漏掉导出中的表: {missing}"
assert not extra, f"映射表包含导出中不存在的表: {extra}"

for name, _, body in sections:
    assert "CREATE TABLE" in body, f"{name} 节内没有 CREATE TABLE"
    assert body.count('"QDITP".') >= 1, f"{name} 节内没有 \"QDITP\". 前缀"

# ---------------------------------------------------------------- 变换
def convert(body: str, schema: str) -> str:
    out = body.replace('"QDITP".', f'"{schema}".')
    out = re.sub(r'TABLESPACE "USERS"[ \t]*', '', out)
    out = re.sub(r"\n[ \t]*;[ \t]*\n", ";\n", out)  # 清掉因删 TABLESPACE 留下的孤行分号
    assert "TABLESPACE" not in out and '"USERS"' not in out, "TABLESPACE 未删净"
    assert f'"{schema}".' in out, "schema 前缀未改写"
    return out.strip("\n")


FILE_HEADER = """-- ============================================================================
-- {schema} 域建表脚本（{desc}）
-- 来源：deploy/sche/sche.sql（2026-10-23 老库 QDITP 全量导出，175 对象；本域 {n} 张表）
-- 生成：docs/ops/environments/scripts/migration/20261023_split_sche_by_domain.py
-- 执行纪律（MUST）：
--   1. 以域用户执行：sqlplus {schema}/<pwd>@//<红岛PDB> @{schema}.sql
--      NEVER 用 SYS 执行（owner 错且落 SYSTEM 表空间）
--   2. 顺序 MUST：PLAT → USER → RIDE → TXN（PLAT 是被引用方）
--   3. 本文件已移除全部 TABLESPACE "USERS" 子句 → 表落执行用户的默认表空间；
--      红岛 MUST 先执行 M1 基础设施（建 4 个域用户 + 绑定 ITP_{ts}_TS 默认表空间）。
--      若尚未建默认表空间、想先落 USERS：把 TABLESPACE "USERS" 全局替换回来即可
--   4. 幂等：重复执行报 ORA-00955（名称已存在）可忽略；单表失败不阻断，MUST 回查输出
--   5. 独立索引不在本文件（老库导出未含，见 README §6）：建表后 MUST 执行
--      _补充索引_本地DDL_需与老库核对.sql，否则幂等唯一键缺失
--   6. 跨域共享（同义词 + 只读授权）、灌数、序列校准不在本文件，见
--      docs/ops/environments/scripts/migration/20261014_create_split_grants_synonyms.sql 与 bundle 跨库脚本
-- ============================================================================
"""

DOMAIN_META = {
    "PLAT": ("平台公共（黑名单/密钥/参数/ACC 设备/管理后台/网关与 AGM 基础设施/collect-pay 旧表）", "PLAT"),
    "USER": ("用户中心（账户/员工卡/开户/逻辑卡池/支付宝账户域）", "USER"),
    "RIDE": ("乘运（乘车码/过闸/票卡状态）", "RIDE"),
    "TXN": ("交易支付（支付签约/支付宝支付/闸机扣费/设备购票/日票/日终对账）", "TXN"),
}

buckets = {"PLAT": [], "USER": [], "RIDE": [], "TXN": []}
for name, header, body in sections:
    if name in EXCLUDED:
        continue
    dom = DOMAIN[name]
    schema = f"QDITP_{dom}"
    buckets[dom].append(f"-- {schema}.{name} definition\n\n{convert(body, schema)}")

for dom, items in buckets.items():
    desc, ts = DOMAIN_META[dom]
    schema = f"QDITP_{dom}"
    head = FILE_HEADER.format(schema=schema, desc=desc, n=len(items), ts=ts)
    path = OUT / f"{schema}.sql"
    path.write_text(head + "\n" + "\n\n".join(items) + "\n", encoding="utf-8")
    print(f"[OK] {path.name}: {len(items)} 张表")

# ---------------------------------------------------------------- 未纳入文件
EXCL_REASON = {
    "STATION_INFO": "manifest 2026-10-17 裁决【不迁】：ticket-server 旧站表、覆盖不全，RIDE 域改同义词读 QDITP_PLAT.TBL_STATION_INFO（确认 ticket-server 仍需直连则改归 RIDE）",
}
lines = [
    "-- ============================================================================",
    "-- 未纳入四域脚本的对象（11 个）——语句整段注释保留， NEVER 直接执行本文件",
    "-- 来源：deploy/sche/sche.sql（2026-10-23 老库 QDITP 全量导出）",
    "-- ============================================================================",
    "",
]
for name, header, body in sections:
    if name not in EXCLUDED:
        continue
    reason = EXCL_REASON.get(name, "CTAS 快照表（变更前 BAK_<表>_<日期> 备份），红岛重建不需要；如需重建删除下行每行行首的 '-- '")
    lines.append(f"-- ---------- {name} ----------")
    lines.append(f"-- 【不纳入原因】{reason}")
    for ln in (header + "\n" + body).splitlines():
        lines.append(("-- " + ln).rstrip())
    lines.append("")
(OUT / "_未纳入_BAK快照与STATION_INFO.sql").write_text("\n".join(lines), encoding="utf-8")
print(f"[OK] _未纳入_BAK快照与STATION_INFO.sql: {len(EXCLUDED)} 个对象（注释态）")

# ---------------------------------------------------------------- 补充索引（本地 DDL 来源）
# 老库导出只含「表 + 内联约束 + 约束背书索引」；本地 DDL 的独立索引几乎全部缺失。
# 这里把本地 DDL 中的独立索引按域归位生成候选清单；执行前 MUST 与老库 all_indexes 核对。
exp = text  # 源导出（含约束名，用于去重）
exp_idx_names = set(re.findall(r'CREATE\s+(?:UNIQUE\s+)?INDEX\s+"QDITP"\."(\w+)"', exp, re.I))
exp_cons_names = set(re.findall(r'CONSTRAINT\s+"(\w+)"', exp))

local_indexes = {}  # name -> dict(unique, table, stmt, src)
for p in sorted(REPO.rglob("*.sql")):
    sp = str(p)
    if any(x in sp for x in ("/target/", "deploy/data/", "deploy/sche/", "node_modules", "/.git/")):
        continue
    try:
        t = p.read_text(encoding="utf-8", errors="replace")
    except Exception:
        continue
    for m in re.finditer(r"(EXECUTE\s+IMMEDIATE\s+')?(CREATE\s+(UNIQUE\s+)?INDEX\s+\"?(\w+)\"?\s+ON\s+\"?(\w+)\"?(.*?);)", t, re.S | re.I):
        dyn, uniq, iname, table = m.group(1), m.group(3), m.group(4), m.group(5)
        if iname in exp_idx_names or iname in exp_cons_names:
            continue  # 导出已有（约束背书）
        if iname in local_indexes:
            continue  # 同名重复定义，取首个
        stmt = re.sub(r"\s+", " ", m.group(2)).strip()
        if dyn:
            # EXECUTE IMMEDIATE '...' 包裹：'' 是字符串转义假象，尾 ' 是串尾引号，都要还原
            stmt = stmt.replace("''", "'").rstrip(";'").strip()
        else:
            stmt = stmt.rstrip(";").strip()
        stmt = re.sub(r"TABLESPACE\s+\w+", "", stmt)          # 与四域文件纪律一致：不写表空间
        stmt = re.sub(r'(ON\s+"?\w+"?)\s*\(', r'\1 (', stmt, flags=re.I)  # 补 TABLE( 与 TABLE ( 之间的空格
        stmt = re.sub(r"\s{2,}", " ", stmt).strip()
        clean = bool(re.match(r'^CREATE (?:UNIQUE )?INDEX "?\w+"?(?:\.\s*"?\w+"?)? ON "?(?:\w+"\.)?"?\w+"? \(.*\)(?: (?:GLOBAL|LOCAL))?$', stmt, re.I))
        local_indexes[iname] = {
            "unique": bool(uniq),
            "table": table.upper(),
            "stmt": stmt,
            "clean": clean,
            "src": sp.replace(str(REPO) + "/", ""),
        }

idx_by_dom = {"PLAT": [], "USER": [], "RIDE": [], "TXN": [], "UNKNOWN": []}
for iname, d in sorted(local_indexes.items()):
    dom = DOMAIN.get(d["table"])
    idx_by_dom[dom if dom else "UNKNOWN"].append((iname, d))

idx_lines = [
    "-- ============================================================================",
    "-- 独立索引补充候选清单（来源：仓库本地 DDL，NEVER 当作老库已核实的事实）",
    "-- 背景：deploy/sche/sche.sql 老库导出只含「表 + 内联约束 + 约束背书索引」，",
    "--       本地 DDL 中的独立索引几乎全部不在导出里——其中 34 个是唯一索引/幂等键",
    "--       （F2F_*/PAY_TXN_DETAIL/GATE_TXN_PAY/QRCODE_TXN_DETAIL/DAILY_TICKET_*/SUPPLEMENT_* 等），",
    "--       漏建 = 幂等防线丢失，金融级系统 MUST 补。",
    "-- 执行前 MUST（缺一不可）：",
    "--   1. 对老库 AFCITPDB 执行 all_indexes 现况核对（本机当前网络不可达库，待可达时补核），",
    "--      确认每个索引在老库真实存在、列与唯一性与本地 DDL 一致（防本地漂移/幻影索引）",
    "--   2. 在四域建表脚本之后、灌数之前执行（唯一索引先于数据，避免灌数后建索引失败）",
    "--   3. 分区表索引已带 GLOBAL/LOCAL 限定（照抄本地 DDL）；F2F_* 的 GLOBAL 唯一索引",
    "--      DROP/TRUNCATE PARTITION 会致 UNUSABLE，是 M0 裁定项",
    "--   4. 以对应域用户执行；表名已按域加 schema 前缀",
    "--   5. 核对方式（网络可达老库时 MUST 先跑）：",
    "--      python3 docs/ops/environments/scripts/migration/20261023_verify_indexes_against_db.py",
    "--      连上 172.20.222.3:1521/AFCITPDB 后逐索引比对 all_indexes/all_ind_columns，",
    "--      并把核对结论（存在列一致 / 列漂移 / 老库不存在）回写进本文件；",
    "--      连不上时该脚本只报网络原因、NEVER 改文件",
    "-- ============================================================================",
    "",
]
for dom in ("PLAT", "USER", "RIDE", "TXN", "UNKNOWN"):
    items = idx_by_dom[dom]
    if not items:
        continue
    if dom == "UNKNOWN":
        idx_lines.append("-- ==================== 表域未知（MUST 人工裁决后归位） ====================")
    else:
        idx_lines.append(f"-- ==================== QDITP_{dom}（{len(items)} 个） ====================")
    for iname, d in items:
        note = "UNIQUE" if d["unique"] else "INDEX"
        if dom == "UNKNOWN":
            idx_lines.append(f"-- {iname} [{note}] ON {d['table']}  -- 来源 {d['src']}")
            idx_lines.append(f"-- TODO: 表 {d['table']} 不在四域映射内（可能是不在老库导出中的表，如 TBL_DEVICE_INFO；"
                             f"或不纳入的 STATION_INFO/BAK_*）——归域后补 schema 前缀再执行")
            idx_lines.append(d["stmt"] + ";")
        else:
            idx_lines.append(f"-- {iname} [{note}] ON {d['table']}  -- 来源 {d['src']}")
            if not d.get("clean", True):
                idx_lines.append("-- ⚠️ 本地 DDL 原文不规范（多余引号/括号不齐/函数式索引等），已原样保留，执行前 MUST 人工修正")
            stmt = d["stmt"]
            # 加 schema 前缀（索引名与表名）
            stmt = re.sub(r'^(CREATE\s+(?:UNIQUE\s+)?INDEX\s+)"?(\w+)"?(\s+ON\s+)"?(\w+)"?',
                          lambda mm: mm.group(1) + f'"QDITP_{dom}"."{mm.group(2)}"' + mm.group(3) + f'"QDITP_{dom}"."{mm.group(4)}"',
                          stmt, count=1, flags=re.I)
            idx_lines.append(stmt + ";")
        idx_lines.append("")
(OUT / "_补充索引_本地DDL_需与老库核对.sql").write_text("\n".join(idx_lines), encoding="utf-8")
n_uniq = sum(1 for v in local_indexes.values() if v["unique"])
print(f"[OK] _补充索引_本地DDL_需与老库核对.sql: {len(local_indexes)} 个（唯一索引 {n_uniq} 个）")

# ---------------------------------------------------------------- README 映射总表
rows = []
for dom in ("PLAT", "USER", "RIDE", "TXN"):
    tbls = sorted(n for n in names if DOMAIN.get(n) == dom)
    rows.append(f"\n### QDITP_{dom}（{len(tbls)} 张）\n")
    rows.append("| # | 表 | 决策来源 |")
    rows.append("|---|---|---|")
    for i, t in enumerate(tbls, 1):
        rows.append(f"| {i} | {t} | {source_of(t)} |")
excl_rows = "\n".join(f"| {t} | {'不迁（同义词方案）' if t == 'STATION_INFO' else 'CTAS 快照，重建不需要'} |" for t in sorted(EXCLUDED))
uniq_idx_rows = "\n".join(
    f"| {iname} | {d['table']} | {'UNIQUE（幂等键）' if d['unique'] else 'INDEX'} | {d['src']} |"
    for iname, d in sorted(local_indexes.items()) if d["unique"])

readme = f"""# 老库导出 scheme 按域拆分（2026-10-23）

**源**：`deploy/sche/sche.sql` —— 2026-10-23 08:27 从老库 `172.20.222.3:1521/AFCITPDB`（schema `QDITP`）导出的全量 DDL，**175 个对象（全部是 CREATE TABLE；索引/约束见 §6 缺口说明）**。
**生成器**：`docs/ops/environments/scripts/migration/20261023_split_sche_by_domain.py`（可重跑；映射写死在脚本里，断言全覆盖 175 张，漏一张即报错）。
**用途**：红岛环境数据库 scheme 重建。与 2026-10-17 那版（`docs/ops/environments/scripts/migration/20261017_schema_*.sql`，DDL 取自仓库本地 SQL）的关系：**那版防老库漂移、这版对齐老库实况**，两版并存；表结构以本版为基线，索引以本地 DDL 为基线（见 §6）。

## 1. 文件与执行顺序

| 文件 | 目标 schema | 表数 | 说明 |
|---|---|---|---|
| `QDITP_PLAT.sql` | QDITP_PLAT | {len([n for n in names if DOMAIN.get(n) == 'PLAT'])} | 平台公共，MUST 第一个建（被引用方） |
| `QDITP_USER.sql` | QDITP_USER | {len([n for n in names if DOMAIN.get(n) == 'USER'])} | 用户中心 |
| `QDITP_RIDE.sql` | QDITP_RIDE | {len([n for n in names if DOMAIN.get(n) == 'RIDE'])} | 乘运 |
| `QDITP_TXN.sql` | QDITP_TXN | {len([n for n in names if DOMAIN.get(n) == 'TXN'])} | 交易支付 |
| `_补充索引_本地DDL_需与老库核对.sql` | 四域 | {len(local_indexes)} | **独立索引候选清单**（本地 DDL 来源，见 §6） |
| `_未纳入_BAK快照与STATION_INFO.sql` | — | 11 | 注释态，NEVER 直接执行 |

执行纪律（与 2026-10-17 版一致，详见各文件头）：**以域用户执行**（NEVER SYS）；顺序 PLAT→USER→RIDE→TXN；**已删全部 `TABLESPACE "USERS"` 子句**，表落域用户默认表空间（红岛 MUST 先建用户并绑 `ITP_<域>_TS`；未建默认表空间就把 `TABLESPACE "USERS"` 替换回去）；建表后 MUST 补独立索引（§6）再灌数；跨域同义词/只读授权/灌数/序列校准不在本文件（见 `20261014_create_split_grants_synonyms.sql` 与 bundle 跨库脚本）。**配套数据导入文件**（同一套域映射，按外键拓扑序拆到 `deploy/data/by-domain/`）见 `20261023_split_data_by_domain.py` 与 `deploy/data/by-domain/README.md`。

## 2. 变换规则（相对老库导出）

1. `"QDITP".<对象>` → `"QDITP_<域>".<对象>`（CREATE TABLE / COMMENT ON / CREATE INDEX / ALTER TABLE 全部改写）。
2. 删除全部 `TABLESPACE "USERS"` 子句（表/约束索引/LOB 三处）。
3. `COMMENT ON TABLE/COLUMN` 全部保留（老库注释是资产；已核对条数守恒：源 1192 列注释 + 115 表注释 = 四域 1188+114 + 未纳入 4+1）。
4. 源文件 CRLF，输出统一 LF（sqlplus 无差异）。
5. **外键零跨域**：全部 `REFERENCES` 均指向本域 schema（PLAT/USER/TXN 各自域内；RIDE 无外键）——拆分不产生跨域外键依赖，这是本次拆分最顺利的一点。
6. 分区表现状（老库实况，与工作记忆「只到 P202612」有出入，MUST 知悉）：
   - 8 张 RANGE 表（GATE_TXN_PAY / QRCODE_TXN_DETAIL / ALIPAY_PAY_TXN_DETAIL / ALIPAY_REFUND_TXN_DETAIL / ALIPAY_PAY_CENTER_MSG_LOG / PAY_TXN_DETAIL / PAY_CALLBACK_LOG / PAY_REFUND_DETAIL）均为 `P202606~P202612`（7 个月度分区，上界 '20270101'）+ **`P_MAX` 兜底分区（VALUES LESS THAN MAXVALUE，SEGMENT CREATION DEFERRED）**。即 2027-01 起新数据不会插失败，但会全部落进 P_MAX，**按月裁剪/归档仍然失效**——红岛重建 MUST 决定：拆 P_MAX 补月度分区，或改建 INTERVAL 自动扩展（F2F_ORDER/F2F_PAYMENT/F2F_RESULT_REPORT 已是 `INTERVAL (NUMTOYMINTERVAL(1,'MONTH'))` 形态，可作样板）。
   - `ALIPAY_PAY_CALLBACK_LOG` 在老库导出中**无分区**（manifest 曾列它为 RANGE）——重建时按需补。
   - `QRCODE_STATUS` / `USER_ITP_REG_INFO` 为 LIST 分区，随建表带入。

## 3. 映射总表（175 = 164 入域 + 11 不纳入）
{''.join(rows)}

### 不纳入（11 张）

| 表 | 原因 |
|---|---|
{excl_rows}

## 4. 待用户确认（本次新裁决，改脚本列表重跑即可，10 秒完事）

1. **22 张 collect-pay 旧表归 PLAT（2026-10-23 用户裁定「放哪都行」，维持 PLAT）**：`TBL_TVM_*`（9）、`TBL_BOM_*`（8）、`BOM_TOPUP_RESULT`、`TBL_APP_ORDER_REFUND`、`TBL_TICKET_REFUND_RECORD`、`TBL_NOTICE_APP_REFUND_RECORD`、`TBL_NOTICE_APP_TAKETICKET_RECORD`。依据：分库计划 §3 勘误的首选建议（PLAT）+ collect-pay 2026-10-13 已下线 0 pod、旧表无新数据 + PLAT 已托管 ACC 设备与 NOTICE 同类表。若日后要改归 TXN（计划给过的备选是「按 tvm-bom-pay 独断」第五域），改本脚本 PLAT/TXN 两个列表重跑即可。
2. **STATION_INFO 不迁**：沿用 manifest 裁决（RIDE 改同义词读 `QDITP_PLAT.TBL_STATION_INFO`）。若红岛确认 ticket-server 仍直连 `STATION_INFO`，需改归 RIDE 或补建。
3. **ALIPAY_PAY_LOG 被 gate-txn-pay 跨域读**（`GateTxnPayManualOpsService`）：归 USER 后 TXN 侧需同义词只读授权或改 RPC，列入 M1 授权清单核对。
4. **10 张 BAK_* 快照**未纳入；红岛若要保留历史备份形态，自行从 `_未纳入_*.sql` 解注。

## 5. 与 2026-10-17 版的表数差

- 2026-10-17：PLAT 76（71+5 RuoYi）/ USER 13（含幻影表 `USER_ITP_REG_INFO_BAK20260909`，库内不存在）/ RIDE 3 / TXN 36 = 128 行清单。
- 本版（老库实况 175）：PLAT 102 / USER 20 / RIDE 3 / TXN 39 = 164 入域 + BAK 10 + STATION_INFO 1。
- 增量全部来自 manifest 未覆盖的表：alipay-account 8（→USER）、gate-txn-pay 拥有的 DISCOUNT_LEVEL / METRO_TRANSFER_PUSH_TASK 与 APP_PAY_LOGS（→TXN）、22 张 collect-pay 旧表（→PLAT，待确认）、AGM_DEVICE_STATUS / GATEWAY_ROUTE_CONFIG* / QRTZ_SIMPROP_TRIGGERS（→PLAT，manifest 缺口裁决）。

## 6. ⚠️ 已知缺口：独立索引不在老库导出里（红岛重建 MUST 补）

老库导出工具只吐了「表 + 内联约束 + 约束背书索引」：全文件仅 2 条独立索引语句（`UK_GATE_TXN_PAY_BIZ` 配 ALTER TABLE 加约束、`IDX_ALIPAY_SIGN_INFO_THIRD_USER_ID`），而**仓库本地 DDL 中的独立索引几乎全部（{len(local_indexes)} 个）不在导出里（含 {n_uniq} 个唯一索引）**。漏建唯一索引 = 幂等防线丢失（AGENTS.md §4.2 幂等设计是硬约束），**NEVER 只靠本导出建红岛 scheme**。

处置：`_补充索引_本地DDL_需与老库核对.sql` 已按域归位全部 {len(local_indexes)} 个候选索引（GLOBAL/LOCAL 限定照抄本地 DDL，表名已加域前缀；`TABLESPACE` 子句已删、`EXECUTE IMMEDIATE '...'` 的 `''` 转义与尾引号已还原、`TABLE(` 缺空格已补——本地 DDL 这三类不规范会原样传导，故在生成端统一净化）。

**核对方式（网络可达老库时 MUST 先跑，一条命令）**：
```bash
python3 docs/ops/environments/scripts/migration/20261023_verify_indexes_against_db.py
```
连上 `172.20.222.3:1521/AFCITPDB`（默认 `qditp/qditp`，可用 `ITP_DB_PASSWORD` 覆盖）后逐索引比对 `all_indexes`/`all_ind_columns`/`all_part_indexes`，判定 ✅一致 / ⚠️列漂移·唯一性·分区类型·状态不一致 / ❌老库不存在，**并把结论按条目回写进补充索引文件**（可重跑刷新）；连不上时只报网络原因、exit 2、NEVER 改文件。函数式索引（`UK_UIRI_*` 的 CASE WHEN 表达式）只验存在性与唯一性、不逐列比。反向缺口（老库有、本地清单与老库导出都没有）会单独列出——那些 MUST 补进红岛 scheme。**本机当前网络不可达该库（2026-10-23 实测 ICMP/TCP 全不通），待可达时执行**。

缺失的唯一索引清单（{n_uniq} 个，按本地 DDL）：

| 索引 | 表 | 类型 | 来源 |
|---|---|---|---|
{uniq_idx_rows}

> 另有 {len(local_indexes) - n_uniq} 个非唯一索引（IDX_*）同样在补充文件里，按域分组列出。
> **UNKNOWN 桶**：少量本地索引所属表不在四域映射内——`TBL_DEVICE_INFO`（para-schema.sql 定义但**老库导出中无此表**，疑幻影或已删，MUST 现查）、`STATION_INFO`（不纳入域，随同义词方案）、`BAK_*` 快照表。这些索引未加 schema 前缀，归域裁决后处理。
"""
(OUT / "README.md").write_text(readme, encoding="utf-8")
print("[OK] README.md 映射总表")

total = sum(len(v) for v in buckets.values())
print(f"\n合计入域 {total} 张 + 不纳入 {len(EXCLUDED)} 张 = {total + len(EXCLUDED)}（导出 175）")
assert total + len(EXCLUDED) == 175
