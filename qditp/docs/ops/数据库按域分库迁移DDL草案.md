# 青岛地铁 ITP 数据库按域分库 — 迁移 DDL 草案

**配套文档**：`docs/ops/数据库按域分库计划.md`（v2，方案 A 逻辑分库 + 4 用户库，已定稿）  
**状态**：**草案 v1.1，仅供评审，严禁直接在生产库执行**。执行前 MUST 由甲方 DBA 复核口令、磁盘组路径，并完成 M0 的 P0 修复。  
**决策记录（2026-10-14 定稿）**：

- 方案：逻辑分库（同实例多 schema）+ **4 个独立表空间 + 4 个独立临时表空间**（USER / TXN / RIDE / PLAT）。
- 临时账号：`itp_ro`（qscg 分流网关只读访问 `USER_ITP_REG_INFO`）。
- 不新建 `itp_ro` 专属表空间；只读账号共享 `USERS` + `TEMP` 即可（若 DBA 要求隔离，按附录 B 另建只读专用表空间）。  
  **安全红线（来自项目约束）**：唯一目标库 `172.20.222.3:1521/AFCITPDB`，无第二环境；`mcp_database_qd` 无只读保护、删除不可回滚。本草案**全部迁移走库内 CTAS 快照 + 同实例可逆（切连接串 / rename）**，绝不在未备份时 DROP 任何源表。

---

## 0. 三大必炸坑（先读）

1. 🔴 **`CREATE TABLE ... AS SELECT` 会剥光分区 + 索引 + 约束**。对分区表、带索引/约束的表，**严禁**用 CTAS 做主迁移。正确做法见 §4：用 `DBMS_METADATA.GET_DDL` 取出**完整结构 DDL**（含 PARTITION 子句、索引、约束），在目标 schema 重建，再 `INSERT /*+ APPEND */` 灌数。
2. 🔴 **`F2F_ORDER` / `F2F_PAYMENT` / `F2F_RESULT_REPORT` 带 GLOBAL 唯一索引**（P0-3）。`DROP/TRUNCATE PARTITION` 或迁移后分区维护会让全局索引 **UNUSABLE**，导致该表读写全挂。M0 优先改 LOCAL；若改不动（唯一键不含分区键），迁移期所有分区 DDL **必须**带 `UPDATE GLOBAL INDEXES`。
3. 🔴 **跨库只读引用靠同义词 + 授权，不能跨库 JOIN 交易数据**。PLAT 拥有公共引用表，USER/TXN/RIDE 建私有同义词 + `GRANT SELECT`；`USER_ITP_REG_INFO` 对网关 `itp_ro` 只读授权。

> 其余约束见计划文档 §5/§7：迁移窗口内停该库定时任务或加 `@SchedulerLock`（双跑重复扣费硬约束）；逻辑分库不扩容，容量 P0-4 须配合 M0 磁盘均衡。

---

## 1. M0：P0 修复（最高优先，2026-12-15 前完成）

### 1.1 盘点当前分区上界（先跑，确认哪些只建到 P202612）

```sql
-- 以 QDITP 执行
SELECT table_name, partition_name, partition_position, high_value
FROM   user_tab_partitions
WHERE  table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'APP_PAY_SIGN_INFO'   -- 按需补充其余分区表
)
ORDER BY table_name, partition_position;
```

### 1.2 补建 2027 分区（模板，按 1.1 的 high_value 类推，每月一个）

```sql
-- 示例：F2F_ORDER 在 P202612 之后补 P202701
ALTER TABLE QDITP.F2F_ORDER
  ADD PARTITION P202701 VALUES LESS THAN (TO_DATE('2027-02-01','YYYY-MM-DD'))
  TABLESPACE <F2F_TS>;   -- 表空间待 DBA 确认（见 §11）
-- 其余分区表同法补到至少 P202712；建议写成循环脚本
```

### 1.3 F2F GLOBAL → LOCAL 唯一索引（先验证可行性，再决定）

```sql
-- 查看 F2F_* 现有全局索引
SELECT i.table_name, i.index_name, i.uniqueness, i.partitioned
FROM   user_indexes i
WHERE  i.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  i.partitioned = 'NO';   -- GLOBAL 索引

-- ⚠️ LOCAL UNIQUE 必须把【分区键】纳入唯一约束；先确认业务唯一键是否含分区键：
--   若含 → 删旧 GLOBAL，按 (原唯一列 + 分区键) 重建为 LOCAL UNIQUE；
--   若不含 → 不能改 LOCAL，迁移期所有分区 DDL 一律带 UPDATE GLOBAL INDEXES（见 §7.3）。
-- 重建示例（仅当确认可行时）：
-- ALTER TABLE QDITP.F2F_ORDER DROP CONSTRAINT <原唯一约束>;
-- CREATE UNIQUE INDEX QDITP.IDX_F2F_ORDER_UK ON QDITP.F2F_ORDER(<列>,<分区键>) LOCAL;
-- ALTER TABLE QDITP.F2F_ORDER ADD CONSTRAINT <原唯一约束> UNIQUE(<列>,<分区键>) USING INDEX QDITP.IDX_F2F_ORDER_UK;
```

> 此步是否执行由 DBA 与业务方裁定；**未裁定前，F2F\_* 迁移与归档一律用 `UPDATE GLOBAL INDEXES` 兜底**。

---

## 2. 新建 4 个独立表空间 + 4 个 schema（独立命名，已预填充）

> 以下值已按各域实际负载特征预填充：块大小 / 分区管理 / 初始大小 / 自动扩展步长与上限 / 临时 uniform size 均已给定；**仅数据文件路径由 DBA 填入真实 ASM / 裸设备路径**（见 §2.1 下方「路径命名规则」与 §11 第 3–4 项）。

### 2.1 独立表空间（4 张）

| 表空间 | 默认 schema | 业务说明 | 初始大小 | 自动扩展步长 | 上限 | 块大小 | 分区管理 | 段管理 |
|---|---|---|---|---|---|---|---|---|
| `ITP_TXN_TS` | `QDITP_TXN` | 交易流水 + 对账源（容量热点集中） | 2 GB | 256 MB | UNLIMITED | 8 KB | LOCAL AUTOALLOCATE | AUTO |
| `ITP_PLAT_TS` | `QDITP_PLAT` | 公共引用数据（~77 张表，规模最大） | 1 GB | 128 MB | UNLIMITED | 8 KB | LOCAL AUTOALLOCATE | AUTO |
| `ITP_USER_TS` | `QDITP_USER` | 用户身份与凭证（~13 张表） | 512 MB | 128 MB | UNLIMITED | 8 KB | LOCAL AUTOALLOCATE | AUTO |
| `ITP_RIDE_TS` | `QDITP_RIDE` | 乘运行为状态（~4 张表，含 QRCODE_TXN_DETAIL 分区热点） | 512 MB | 128 MB | UNLIMITED | 8 KB | LOCAL AUTOALLOCATE | AUTO |

```sql
-- 以下 DATAFILE 路径为预填模板，DBA 按下方「路径命名规则」替换后执行
CREATE TABLESPACE ITP_TXN_TS
  DATAFILE '+RACDG/itp/ITP_TXN_TS01.dbf' SIZE 2G AUTOEXTEND ON NEXT 256M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_PLAT_TS
  DATAFILE '+RACDG/itp/ITP_PLAT_TS01.dbf' SIZE 1G AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_USER_TS
  DATAFILE '+RACDG/itp/ITP_USER_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_RIDE_TS
  DATAFILE '+RACDG/itp/ITP_RIDE_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;
```

**路径命名规则（DBA 必填，替换 `+RACDG/itp/` 前缀为真实 ASM 磁盘组 + 目录，或换裸设备路径）**：
- 格式：`<磁盘组>/<实例名或cdb>/<表空间名>.dbf`
- 示例 ASM：`+RACDG/ITP/ITP_TXN_TS01.dbf`（已列）
- 示例裸设备：`/data1/oradata/ITP/ITP_TXN_TS01.dbf`
- 多盘均衡：若 DBA 要求热表空间跨盘，补建第二份 DATAFILE（如 `ITP_TXN_TS02.dbf` 落 `+RACDG2`）
- 命名 MUST 遵循：`ITP_<域>_TS<序号>.dbf`，避免与既有 ITP 表空间重名

### 2.2 独立临时表空间（4 套）

| 临时表空间 | 默认 schema | 初始大小 | 自动扩展步长 | 上限 | uniform size |
|---|---|---|---|---|---|
| `ITP_TXN_TEMP` | `QDITP_TXN` | 512 MB | 64 MB | UNLIMITED | 1 MB |
| `ITP_USER_TEMP` | `QDITP_USER` | 256 MB | 64 MB | UNLIMITED | 1 MB |
| `ITP_RIDE_TEMP` | `QDITP_RIDE` | 256 MB | 64 MB | UNLIMITED | 1 MB |
| `ITP_PLAT_TEMP` | `QDITP_PLAT` | 256 MB | 64 MB | UNLIMITED | 1 MB |

```sql
-- 路径同 2.1，替换 <PATH> 为真实值（建议与对应 TS 同源或同磁盘组）
CREATE TEMPORARY TABLESPACE ITP_TXN_TEMP
  TEMPFILE '+RACDG/itp/ITP_TXN_TEMP01.dbf' SIZE 512M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_USER_TEMP
  TEMPFILE '+RACDG/itp/ITP_USER_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_RIDE_TEMP
  TEMPFILE '+RACDG/itp/ITP_RIDE_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_PLAT_TEMP
  TEMPFILE '+RACDG/itp/ITP_PLAT_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;
```

### 2.3 4 个用户（每用户绑定独立默认表空间 + 独立临时表空间，配额 UNLIMITED）

```sql
CREATE USER QDITP_USER  IDENTIFIED BY "<QDITP_USER_PWD>"
  DEFAULT TABLESPACE ITP_USER_TS
  TEMPORARY TABLESPACE ITP_USER_TEMP;
CREATE USER QDITP_TXN   IDENTIFIED BY "<QDITP_TXN_PWD>"
  DEFAULT TABLESPACE ITP_TXN_TS
  TEMPORARY TABLESPACE ITP_TXN_TEMP;
CREATE USER QDITP_RIDE  IDENTIFIED BY "<QDITP_RIDE_PWD>"
  DEFAULT TABLESPACE ITP_RIDE_TS
  TEMPORARY TABLESPACE ITP_RIDE_TEMP;
CREATE USER QDITP_PLAT  IDENTIFIED BY "<QDITP_PLAT_PWD>"
  DEFAULT TABLESPACE ITP_PLAT_TS
  TEMPORARY TABLESPACE ITP_PLAT_TEMP;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SYNONYM,
      CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER,
      CREATE MATERIALIZED VIEW, UNLIMITED TABLESPACE
  TO QDITP_USER, QDITP_TXN, QDITP_RIDE, QDITP_PLAT;
```

> 口令 `<QDITP_*_PWD>` 不入 VCS；落地 K8s secret 与各模块 `application.properties` 的 `other.sql.password`（项目记忆已确认 alipay-pay-sign-server 当前为唯一有效口令，迁移期须统一重新分发）。

### 2.4 网关只读账号（共享 USERS + TEMP，不建独立表空间）

```sql
CREATE USER itp_ro IDENTIFIED BY "<ITP_RO_PWD>";
GRANT CREATE SESSION, CREATE SYNONYM TO itp_ro;
-- 不授 UNLIMITED TABLESPACE；它只读 USER_ITP_REG_INFO（通过 GRANT + 同义词）
-- 如 DBA 要求只读账号独立表空间，见附录 B
```

---

## 附 B：可选 —— 只读账号专用表空间（若 DBA 要求隔离）

```sql
CREATE TABLESPACE ITP_RO_TS
  DATAFILE '+RACDG/itp/ITP_RO_TS01.dbf' SIZE 64M  AUTOEXTEND ON NEXT 32M  MAXSIZE 512M
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 64K
  SEGMENT SPACE MANAGEMENT AUTO;

ALTER USER itp_ro DEFAULT TABLESPACE ITP_RO_TS QUOTA UNLIMITED ON ITP_RO_TS;
-- 临时仍用系统 TEMP；若需独立临时可再建 ITP_RO_TEMP 并 ALTER USER ... TEMPORARY TABLESPACE ITP_RO_TEMP;
```

> 默认推荐**共享**（本草案 2.4 已给出）；仅当 DBA 审计 / 成本归因需要时才启用独立表空间。路径同 §2.1 命名规则。

---

## 3. 前置盘点（迁移前必跑，决定哪些额外对象要带走）

```sql
-- 3.1 序列（表用 Oracle 序列/IDENTITY 时，需一并迁到目标 schema）
SELECT sequence_name, last_number
FROM   user_sequences
WHERE  sequence_name IN ( SELECT 'SEQ_'||table_name FROM user_tables );  -- 命名约定需实际核对

-- 3.2 触发器
SELECT trigger_name, table_name, status
FROM   user_triggers
WHERE  table_name IN ( /* §5/§6/§7 的待迁表清单 */ );

-- 3.3 跨表外键（迁后需重新指向目标 schema 或经同义词）
SELECT a.constraint_name, a.table_name, a.r_constraint_name, b.table_name AS ref_table
FROM   user_constraints a
JOIN   user_constraints b ON a.r_constraint_name = b.constraint_name
WHERE  a.constraint_type = 'R';

-- 3.4 当前 130 表是否都在 QDITP（与计划 §3 对账）
SELECT COUNT(*) FROM user_tables;   -- 期望 130
```

> 序列/触发器/外键的迁移见 §4 步骤 1–2（用 `DBMS_METADATA.GET_DDL('SEQUENCE'|'TRIGGER'|'REF_CONSTRAINT', ...)` 取 DDL，替换 schema 后在目标执行）。

---

## 4. 单表安全迁移流程（保留分区 + 索引 + 约束）

以 `GATE_TXN_PAY` 迁往 `QDITP_TXN` 为例，其余表照此套用。

```sql
-- 步骤 0：CTAS 快照兜底（QDITP 下，永远先备）
CREATE TABLE QDITP.BAK_GATE_TXN_PAY_20261215 AS SELECT * FROM QDITP.GATE_TXN_PAY;

-- 步骤 1：取源表完整 DDL（含分区），替换 schema 后在目标执行
--         ⚠️ 不要直接 CTAS，否则分区/索引全丢
SELECT DBMS_METADATA.GET_DDL('TABLE', 'GATE_TXN_PAY', 'QDITP') FROM dual;
-- → 编辑输出：把 "QDITP." 改为 "QDITP_TXN."，去掉存储子句里写死的旧 schema；
--   在 QDITP_TXN 下执行，得到带 PARTITION 的空表。

-- 步骤 2：取索引 + 约束 DDL，目标执行
SELECT DBMS_METADATA.GET_DDL('INDEX', index_name, 'QDITP')
FROM   user_indexes WHERE table_name = 'GATE_TXN_PAY';
-- → 替换 schema 后执行（GLOBAL 索引见 §1.3 / §7.3 处理）

-- 步骤 3：灌数（源表大时 NOLOGGING + APPEND 提速；分区表逐分区或整体均可）
INSERT /*+ APPEND */ INTO QDITP_TXN.GATE_TXN_PAY SELECT * FROM QDITP.GATE_TXN_PAY;
COMMIT;

-- 步骤 4：补序列/触发器（若有），并收集统计
-- DBMS_STATS.GATHER_TABLE_STATS('QDITP_TXN','GATE_TXN_PAY');

-- 步骤 5：源表保留为回滚锚点（重命名，不 DROP）
--         ⚠️ 连接串切回 QDITP 即秒级回滚； cooldown 后才考虑 DROP
-- ALTER TABLE QDITP.GATE_TXN_PAY RENAME TO QDITP.GATE_TXN_PAY_PRE_SPLIT;
```

**通用模板（可参数化为脚本）**

```
表清单 → 循环：
  BAK_<t>_YYYYMMDD  ← CTAS 快照
  GET_DDL(TABLE)    → 目标重建（替换 schema）
  GET_DDL(INDEX/CONSTRAINT) → 目标重建
  INSERT APPEND     → 灌数
  序列/触发器       → 目标重建
  源表 RENAME _PRE_SPLIT（不 DROP）
```

---

## 5. M1：QDITP_PLAT 建库 + 共享暴露（风险最低，先行）

PLAT 拥有公共引用表（黑名单 / 参数票价 / 密钥 / SYS / QRTZ / ACC 设备 / 脚本落地表），并承担对全域的只读授权。

### 5.1 迁 PLAT 名下表（§3.4 全部 77 张，逐张走 §4）

> 表清单见计划 §3.4。迁移后 `web-server`（管理后台）的 `datasource` 指向 `QDITP_PLAT`。

### 5.2 USER_ITP_REG_INFO 对网关 itp_ro 只读授权（分流定案已依赖）

```sql
-- USER 库建好后，对网关只读账号 itp_ro 授权 + 建私有同义词（由 itp_ro 或 DBA 建）
GRANT SELECT ON QDITP_USER.USER_ITP_REG_INFO TO itp_ro;
-- itp_ro 侧（qdscg 已按此实现，JDBC 用 itp_ro 连接）：
CREATE SYNONYM itp_ro.USER_ITP_REG_INFO FOR QDITP_USER.USER_ITP_REG_INFO;
-- 取消注释改用公共同义词（若不想在每个只读账号建私有同义词）：
-- CREATE PUBLIC SYNONYM USER_ITP_REG_INFO FOR QDITP_USER.USER_ITP_REG_INFO;
```

### 5.3 STATION_INFO 技术债收敛（RIDE 的 STATION_INFO 改同义词，零代码改动）

> ticket-server 代码查 `STATION_INFO`；迁 RIDE 后把 RIDE 的 `STATION_INFO` 退场，改为指向 PLAT 的 `TBL_STATION_INFO` 的同义词，ticket-server **不改代码**。

```sql
-- 迁 RIDE 时：STATION_INFO 不入 RIDE 实体表，改为同义词
CREATE SYNONYM QDITP_RIDE.STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;
GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_RIDE;
-- 旧 QDITP.STATION_INFO 在迁 RIDE 后保留为 BAK_STATION_INFO_YYYYMMDD 备份，确认无误再退场
```

---

## 6. M2：QDITP_USER + QDITP_RIDE 迁移

### 6.1 QDITP_USER（§3.1，~13 张，逐张走 §4）

`ACCOUNT_EXCEPTION_TICKET`、`ALIPAY_PHONE_CHANGE_LOG`、`APP_USER_PAY_CHANNEL`(USER 拥有)、`USER_ACC_EMPLOYEE_CARD`、`USER_ACC_EMPLOYEE_CARD_LOG`、`USER_ACC_TICKETNO`、`USER_ITP_REG_INFO`、`USER_ITP_REG_INFO_BAK20260909`、`USER_ITP_REG_LOG`、`USER_PHONE_CHANGE_LOG`、`LOGIC_CARD_POOL_BATCH`、`LOGIC_CARD_POOL_CARD`、`LOGIC_CARD_POOL_TYPE_LOCK`。

> 迁移后 `account-server` / `alipay-account-server` / `card-pool-server` 的 `datasource` 指向 `QDITP_USER`。

### 6.2 QDITP_RIDE（§3.3，4 张，逐张走 §4）

`QRCODE_STATUS`、`QRCODE_TXN_DETAIL`(分区)、`QRCODE_SUPPLEMENT_REQUEST`、`STATION_INFO`(按 §5.3 建同义词，非实体表)。

> 迁移后 `ticket-server` 的 `datasource` 指向 `QDITP_RIDE`。`QRCODE_TXN_DETAIL` 为分区表，按 §4 保留分区；属容量热点（§附），优先排归档。


### 6.3 USER / RIDE 需要的 PLAT 共享同义词（见 §8 清单）

- USER 域：引用 `BLACKLIST*`、`TBL_*` 参数/票价、`APP_USER_PAY_CHANNEL`(自身拥有，无需同义词)。
- RIDE 域：`STATION_INFO`(§5.3) + `BLACKLIST*` + 必要 `TBL_*`。

---

## 7. M3：QDITP_TXN 迁移（容量热点，须双跑锁）

### 7.1 表清单（§3.2，~36 张，逐张走 §4）

- pay-sign：`APP_PAY_SIGN_INFO`、`APP_PAY_SIGN_LOG`、`APP_PAY_SIGN_REQUEST`、`APP_TERMINATION_REQUEST`、`PAY_TXN_DETAIL`、`PAY_REFUND_DETAIL`、`PAY_CALLBACK_LOG`
- alipay-pay-sign：`ALIPAY_PAY_TXN_DETAIL`、`ALIPAY_REFUND_TXN_DETAIL`、`ALIPAY_PAY_CALLBACK_LOG`、`ALIPAY_PAY_CENTER_MSG_LOG`（均 RANGE 分区）
- gate-txn-pay：`GATE_TXN_PAY`(分区)、`GATE_RETRY_QUEUE`、`SUPPLEMENT_ORDER`、`SUPPLEMENT_ORDER_ITEM`
- face-pay：`F2F_ORDER`、`F2F_PAYMENT`、`F2F_REFUND`、`F2F_RESULT_REPORT`、`F2F_TICKET`、`F2F_DEVICE_STATUS`、`F2F_NOTIFY_TASK`（7 张**全 RANGE 分区**，前 3 张带 GLOBAL 唯一索引）
- daily-ticket：`DAILY_TICKET_ORDER`、`DAILY_TICKET_INSTANCE`、`DAILY_TICKET_PAY_LOG`、`DAILY_TICKET_PAY_NOTIFY`、`DAILY_TICKET_REFUND`、`DAILY_TICKET_REFUND_DETAIL`、`DAILY_TICKET_USAGE_LOG`、`TRAVEL_TICKET_ORDER`
- recon：`RECON_BATCH`、`RECON_BATCH_FILE`、`RECON_BATCH_PART`、`RECON_BATCH_SOURCE`、`RECON_THIRD_BILL`、`RECON_TXN_DIFF`（与源表 GATE/DAILY/F2F 同库，**对账零跨库读**）

> 迁移后 `pay-sign-server` / `alipay-pay-sign-server` / `gate-txn-pay-server` / `face-pay-server` / `daily-ticket-server` / `recon-server` 的 `datasource` 指向 `QDITP_TXN`。

### 7.2 双跑锁（硬约束）

> 迁移窗口内，对 TXN 相关模块的定时任务**停跑或加 `@SchedulerLock`**：recon-server、face-pay-server 的 `@Scheduled`、以及涉及对账文件投递的任务。避免旧库（QDITP）+ 新库（QDITP_TXN）双写导致重复扣费 / 重复投对账文件。

### 7.3 分区 / GLOBAL 索引处理（F2F\_* 重点）

```sql
-- 迁移或后续归档任何分区 DDL，F2F_* 必须带 UPDATE GLOBAL INDEXES：
ALTER TABLE QDITP_TXN.F2F_ORDER
  ADD PARTITION P202702 VALUES LESS THAN (TO_DATE('2027-03-01','YYYY-MM-DD'))
  TABLESPACE <TS_TXN> UPDATE GLOBAL INDEXES;   -- 全局索引兜底，等效全索引重建
-- 若 §1.3 已改 LOCAL，则可省略 UPDATE GLOBAL INDEXES（LOCAL 分区维护自动局部重建）。
```

> 全 TXN 库 4 张容量热点（`PAY_CALLBACK_LOG` / `GATE_TXN_PAY` / `ALIPAY_PAY_CENTER_MSG_LOG` / `ALIPAY_PAY_CALLBACK_LOG`）独占本库，分区维护 / 归档 / 压缩可逐库独立实施 —— 这是分库最实在的运维收益。

### 7.4 TXN 需要的 PLAT 共享同义词（见 §8）

`BLACKLIST*`、计费/票价 `TBL_*`、`APP_USER_PAY_CHANNEL`(指向 USER)。

---

## 8. 跨库共享同义词权威清单（手写关键项）

> 原则：引用数据只读共享 → PLAT + 同义词 + `GRANT SELECT`；交易数据绝不跨库 JOIN。  
> 下列为**必须手写**的跨库项；其余 `TBL_*` 按各模块代码实际引用**扩展**（先 `grep` 各 server 的 Mapper XML 里 `FROM TBL_xxx` 确定最小集）。

### 8.1 USER → PLAT（黑名单 / 参数 / 票价）

```sql
GRANT SELECT ON QDITP_PLAT.BLACKLIST               TO QDITP_USER;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_OPERATE_LOG   TO QDITP_USER;
GRANT SELECT ON QDITP_PLAT.BLACKLIST_RELEASED      TO QDITP_USER;
CREATE SYNONYM QDITP_USER.BLACKLIST             FOR QDITP_PLAT.BLACKLIST;
CREATE SYNONYM QDITP_USER.BLACKLIST_OPERATE_LOG FOR QDITP_PLAT.BLACKLIST_OPERATE_LOG;
CREATE SYNONYM QDITP_USER.BLACKLIST_RELEASED    FOR QDITP_PLAT.BLACKLIST_RELEASED;
-- 参数/票价/线路/站点（示例，按代码引用补齐 TBL_* 全集）
GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_USER;
CREATE SYNONYM QDITP_USER.TBL_STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;
-- ... 其余 TBL_* 同理
```

### 8.2 TXN → PLAT（黑名单 / 计费参数）与 TXN → USER（APP_USER_PAY_CHANNEL）

```sql
GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_TXN;
CREATE SYNONYM QDITP_TXN.BLACKLIST FOR QDITP_PLAT.BLACKLIST;
-- ... 其余 BLACKLIST* / TBL_* 计费参数同理

GRANT SELECT ON QDITP_USER.APP_USER_PAY_CHANNEL TO QDITP_TXN;
CREATE SYNONYM QDITP_TXN.APP_USER_PAY_CHANNEL FOR QDITP_USER.APP_USER_PAY_CHANNEL;
```

### 8.3 RIDE → PLAT（STATION_INFO 已 §5.3 收敛 + 黑名单）

```sql
GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_RIDE;
CREATE SYNONYM QDITP_RIDE.STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;  -- §5.3
GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_RIDE;
CREATE SYNONYM QDITP_RIDE.BLACKLIST FOR QDITP_PLAT.BLACKLIST;
```

### 8.4 密钥 / ACC 设备表（供 fep-dev / acc-es 等接入层只读）

```sql
GRANT SELECT ON QDITP_PLAT.METRO_CA_KEYSTORE       TO <fep_dev_user>;
GRANT SELECT ON QDITP_PLAT.METRO_MEMBER_STATIC_KEY TO <fep_dev_user>;
-- 接入层若无独立 schema，仍连 QDITP 时由 QDITP 建私有同义词指向 PLAT
```

---

## 9. 校验 SQL（M4 全量校验）

```sql
-- 9.1 行数对账（每个迁移动表都比对，示例 GATE_TXN_PAY）
SELECT (SELECT COUNT(*) FROM QDITP.GATE_TXN_PAY_PRE_SPLIT) AS src_cnt,
       (SELECT COUNT(*) FROM QDITP_TXN.GATE_TXN_PAY)        AS dst_cnt
FROM dual;

-- 9.2 索引状态（非分区）
SELECT index_name, status FROM user_indexes
WHERE table_name='F2F_ORDER' AND status <> 'VALID';

-- 9.3 LOCAL 索引各分区段状态（F2F_* 改 LOCAL 后）
SELECT index_name, partition_name, status FROM user_ind_partitions
WHERE index_name IN (SELECT index_name FROM user_indexes WHERE table_name='F2F_ORDER');

-- 9.4 分区存在性（2027 分区是否补齐）
SELECT table_name, partition_name FROM user_tab_partitions
WHERE partition_name LIKE 'P2027%' ORDER BY table_name;

-- 9.5 同义词解析
SELECT synonym_name, table_owner, table_name FROM user_synonyms
WHERE table_owner IN ('QDITP_PLAT','QDITP_USER')
ORDER BY table_owner, synonym_name;
```

---

## 10. 回滚方案（同实例秒级可逆）

1. **连接串切回**：任一模块迁移后异常 → 把该模块 `application.properties` 的 `datasource` 指回 `QDITP`（原 schema），重启即回滚。源表始终保留为 `BAK_*` / `_PRE_SPLIT`，**未 DROP**。
2. **数据回退**：若新库已产生写入需回吐，按业务主键反向 `INSERT ... SELECT` 回 QDITP（仅极端情况）。
3. **同义词回滚**：`DROP SYNONYM` 即可恢复跨库引用到原 QDITP 实体表。
4. **cooldown 后才清理**：M4 校验通过 + 观察期（建议 ≥7 天）后，才 `ALTER TABLE QDITP.<t>_PRE_SPLIT RENAME TO QDITP.BAK_<t>_<date>`，再择期 DROP BAK。

---

## 11. 待甲方 DBA 填项（执行前 MUST 确认）

| # | 项                                            | 取值                                                                                                                     |
| - | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| 1 | 4 个 schema 口令 | `<QDITP_USER_PWD>` / `<QDITP_TXN_PWD>` / `<QDITP_RIDE_PWD>` / `<QDITP_PLAT_PWD>`（不入 VCS，落 K8s secret + 各模块 properties） |
| 2 | `itp_ro` 口令 | `<ITP_RO_PWD>` |
| 3 | **表空间数据文件路径** | 文档 §2.1 已预填 `+RACDG/itp/ITP_<域>_TS01.dbf`，DBA **替换前缀**为真实 ASM 磁盘组 / 裸设备路径（命名 MUST 遵循 `ITP_<域>_TS<序号>.dbf`） |
| 4 | **临时表空间 tempfile 路径** | 文档 §2.2 已预填 `+RACDG/itp/ITP_<域>_TEMP01.dbf`，DBA **替换前缀**为真实路径（建议与对应 TS 同源或同磁盘组） |
| 5 | DB Link 策略 | 默认**不用 DB Link**，跨库只读走同义词 + 授权；如确需，单列审批 |
| 6 | F2F GLOBAL→LOCAL | §1.3 裁定：改 LOCAL 还是迁移期全程 `UPDATE GLOBAL INDEXES` |
| 7 | 只读账号独立表空间 | 默认不建（§2.4），共享 `USERS` + `TEMP`；DBA 要求隔离时启用附录 B |
| 8 | 与「唯一目标库」裁决 | 垂直分区 = 同实例多 schema，不违反 decisions.md 裁决；物理分库（新实例）须单独立项 |

---

### 附：本草案与计划文档的对应关系

- 选型 / 4 库映射 / 全 130 表归属：计划 §1 / §2 / §3
- 跨库共享策略：计划 §4 → 本草案 §8
- 迁移方案（CTAS 兜底 / 建库 / 迁表 / 同义词 / 切 datasource / 双跑锁 / GLOBAL 索引）：计划 §5 → 本草案 §4 / §5 / §7
- 实施里程碑 M0–M4：计划 §6 → 本草案 §1(M0) / §5(M1) / §6(M2) / §7(M3) / §9(M4)
- 风险与回滚：计划 §7 → 本草案 §0 / §10

### 附：4 库 + 网关账号的完整 DDL 语句清单（仅供评审）

> 以下为 §2 的"独立表空间 + 独立临时表空间"版本，**可直接交付 DBA 审批**。文档中所有参数（大小 / 自动扩展 / uniform size）均已预填，**唯一待 DBA 替换的是 `+RACDG/itp/` 前缀**为真实 ASM / 裸设备路径，以及 5 个口令占位符。
> 口令不入本仓库；如需发 DBA 邮件，把 `<QDITP_*_PWD>` / `<ITP_RO_PWD>` 改为实际值，或另发附件。

```sql
-- ==========================================
-- Step 0：权限切换（用 SYSDBA 或具有 DBA 角色的账号执行）
-- ==========================================
-- 确认当前会话有 DBA / CREATE USER / CREATE TABLESPACE 权限

-- ==========================================
-- Step 1：创建 4 个独立表空间
--     路径已预填 ASM 模板 '+RACDG/itp/'，DBA 替换为真实磁盘组 / 裸设备
--     顺序：TXN(最大) → PLAT → USER → RIDE（与容量规划热点对齐）
-- ==========================================
CREATE TABLESPACE ITP_TXN_TS
  DATAFILE '+RACDG/itp/ITP_TXN_TS01.dbf' SIZE 2G AUTOEXTEND ON NEXT 256M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_PLAT_TS
  DATAFILE '+RACDG/itp/ITP_PLAT_TS01.dbf' SIZE 1G AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_USER_TS
  DATAFILE '+RACDG/itp/ITP_USER_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

CREATE TABLESPACE ITP_RIDE_TS
  DATAFILE '+RACDG/itp/ITP_RIDE_TS01.dbf' SIZE 512M AUTOEXTEND ON NEXT 128M MAXSIZE UNLIMITED
  ONLINE PERMANENT BLOCKSIZE 8192
  EXTENT MANAGEMENT LOCAL AUTOALLOCATE
  SEGMENT SPACE MANAGEMENT AUTO;

-- ==========================================
-- Step 2：创建 4 个独立临时表空间（uniform size 1M）
--     路径建议与对应 TS 同源或同磁盘组；'+RACDG/itp/' 替换为 §2.1 的同一路径
-- ==========================================
CREATE TEMPORARY TABLESPACE ITP_TXN_TEMP
  TEMPFILE '+RACDG/itp/ITP_TXN_TEMP01.dbf' SIZE 512M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_USER_TEMP
  TEMPFILE '+RACDG/itp/ITP_USER_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_RIDE_TEMP
  TEMPFILE '+RACDG/itp/ITP_RIDE_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

CREATE TEMPORARY TABLESPACE ITP_PLAT_TEMP
  TEMPFILE '+RACDG/itp/ITP_PLAT_TEMP01.dbf' SIZE 256M AUTOEXTEND ON NEXT 64M MAXSIZE UNLIMITED
  EXTENT MANAGEMENT LOCAL UNIFORM SIZE 1M;

-- ==========================================
-- Step 3：创建 4 个用户（绑定独立默认表空间 + 独立临时表空间）
-- ==========================================
CREATE USER QDITP_USER  IDENTIFIED BY "<QDITP_USER_PWD>"
  DEFAULT TABLESPACE ITP_USER_TS
  TEMPORARY TABLESPACE ITP_USER_TEMP;
CREATE USER QDITP_TXN   IDENTIFIED BY "<QDITP_TXN_PWD>"
  DEFAULT TABLESPACE ITP_TXN_TS
  TEMPORARY TABLESPACE ITP_TXN_TEMP;
CREATE USER QDITP_RIDE  IDENTIFIED BY "<QDITP_RIDE_PWD>"
  DEFAULT TABLESPACE ITP_RIDE_TS
  TEMPORARY TABLESPACE ITP_RIDE_TEMP;
CREATE USER QDITP_PLAT  IDENTIFIED BY "<QDITP_PLAT_PWD>"
  DEFAULT TABLESPACE ITP_PLAT_TS
  TEMPORARY TABLESPACE ITP_PLAT_TEMP;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SYNONYM,
      CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER,
      CREATE MATERIALIZED VIEW, UNLIMITED TABLESPACE
  TO QDITP_USER, QDITP_TXN, QDITP_RIDE, QDITP_PLAT;

-- ==========================================
-- Step 4：网关只读账号（共享 USERS + TEMP，不建独立表空间）
-- ==========================================
CREATE USER itp_ro IDENTIFIED BY "<ITP_RO_PWD>";
GRANT CREATE SESSION, CREATE SYNONYM TO itp_ro;

-- ==========================================
-- Step 5（可选）：迁移专用角色（仅迁移窗口赋给执行账号）
-- ==========================================
CREATE ROLE QDITP_MIGRATOR;
GRANT CREATE ANY TABLE, DROP ANY TABLE, ALTER ANY TABLE, INSERT ANY TABLE,
      SELECT ANY TABLE, CREATE ANY INDEX, DROP ANY INDEX, CREATE ANY SYNONYM,
      CREATE ANY SEQUENCE, CREATE ANY TRIGGER, SELECT ANY DICTIONARY
  TO QDITP_MIGRATOR;
-- 用法：临时 GRANT QDITP_MIGRATOR TO <迁移执行账号>; 迁移结束 REVOKE;

-- ==========================================
-- Step 6（可选）：归档旧 ITP 业务表前的只读权限
-- ==========================================
-- 迁移期间旧 QDITP 表仍作为 BAK_* / _PRE_SPLIT 保留；
-- 若需对旧表做只读审计，可保留原 QDITP 账号的其他读取权限（本项目无此需求）
```

> ✅ 本清单**无需再改参数**——表空间大小 / 自动扩展 / uniform size 均已按域特征预填。唯一待 DBA 做：
> 1. 把 `+RACDG/itp/` 前缀换成真实 ASM 磁盘组 / 裸设备 / 数据文件路径
> 2. 填入 4 个 `<QDITP_*_PWD>` + `<ITP_RO_PWD>` 的真实值
> 3. 如需多盘均衡热表空间，按 §2.1「路径命名规则」补建 `ITP_TXN_TS02.dbf` 等第二份 DATAFILE
