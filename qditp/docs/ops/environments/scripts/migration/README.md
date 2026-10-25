# 青岛地铁 ITP 数据库按域分库 — 迁移执行指南

**配套文档**：`docs/ops/数据库按域分库计划.md`
**配套脚本**：`docs/ops/environments/scripts/migration/` 目录（执行包 + DDL 补充源 + 工具，见 §四）
**版本**：v1.0（2026-10-14）
**状态**：草案，待 DBA 审批后执行

---

## 一、快速导航

| 角色 | 你需要看什么 |
|---|---|
| **DBA** | 手册 §九（待确认项）+ 附录 A（表空间规格）+ `bundle_m1_infra.sql`（建库语句） |
| **运维** | 手册 §六（执行顺序）+ `bundle_m0_partition_fix.sql` / `bundle_m2_m3_migration.sql` / `bundle_m4_verify_rollback.sql` |
| **开发** | 手册 §二（跨库共享策略）+ 附录 D（datasource 切换清单）+ `create_split_grants_synonyms.sql` |
| **测试** | 手册 §三（表归属清单）+ `migration_precheck.sql` / `m0_partition_precheck.sql` |

---

## 二、执行顺序（6 步，串行）

```
Step 1: M0 分区补建（2026-12-15 前必须完成）
        ↓
Step 2: M1 基础建设（表空间 + schema + 授权）
        ↓
Step 3: M2/M3/M4 数据导出（PLAT 先导，USER/RIDE/TXN 并行）
        ↓
Step 4: M2/M3/M4 数据导入（PLAT 先导，USER/RIDE/TXN 并行，TXN 需双跑锁）
        ↓
Step 5: M4 全量校验（行数比对 + 索引/分区/同义词状态 + 切 datasource）
        ↓
Step 6: 观察期 7 天 → DROP BAK_* 备份表
```

### Step 1：M0 分区补建

**目标**：把 15 张分区表的 2027 年分区补齐，避免 2027-01-01 失效。

**操作**：
```bash
# 1.1 跑 precheck 查出真实分区键 + 当前最高上界
sqlplus qditp/qditp@//172.20.222.3:1521/AFCITPDB @docs/ops/environments/scripts/migration/20261014_m0_partition_precheck.sql

# 1.2 生成补建 DDL（每表一个 .sql 文件）
sqlplus qditp/qditp@//172.20.222.3:1521/AFCITPDB @docs/ops/environments/scripts/migration/20261014_m0_generate_add_partition.sql
# 输出：/tmp/qditp_split_ddl/m0_partitions/M0_*.sql（14 个文件）

# 1.3 人工复核每个 .sql 文件
#   - TABLESPACE 是否正确（ITP_TXN_TS / ITP_RIDE_TS）
#   - 月份范围是否正确（P202701 ~ P202712）
#   - F2F_ORDER/PAYMENT/RESULT_REPORT 是否带 UPDATE GLOBAL INDEXES

# 1.4 打包发给 DBA 审批
# 1.5 DBA 在维护窗口执行（建议先跑 1 张测试表确认无 ORA-14xxx 错误）

# 1.6 验证
SELECT table_name, COUNT(*) AS partition_count
FROM   user_tab_partitions
WHERE  table_name IN ('F2F_ORDER','F2F_PAYMENT',...)
  AND  partition_name LIKE 'P2027%'
GROUP BY table_name
ORDER BY table_name;
-- 期望：每张表 12 行（P202701 ~ P202712）
```

**风险控制**：
- ⚠️ F2F_ORDER/PAYMENT/RESULT_REPORT 带 GLOBAL 唯一索引，分区 DDL **必须**带 `UPDATE GLOBAL INDEXES`
- ⚠️ 若唯一键不含分区键，不能改 LOCAL，只能全程兜底 `UPDATE GLOBAL INDEXES`
- ⚠️ 补建分区是追加操作，不回滚数据，安全

---

### Step 2：M1 基础建设

**目标**：创建 4 个 schema + 4 个独立表空间 + 4 个独立临时表空间 + itp_ro 网关只读账号 + 跨库同义词授权。

**操作**：
```bash
# 2.0 ASM 路径决策（2026-10-17 定，方案 A = OMF）
#   实测（红岛 HDTPDB，dba_data_files）：磁盘组 = +DATA，一级目录 = ITPCDB（CDB 级）；
#   hditpdb PDB 的文件在 +DATA/ITPCDB/<PDB_GUID>/DATAFILE|TEMPFILE/ 下（GUID 子目录）
#   ⇒ 已放弃手写路径（平级 ITPCDB/ 写法绕过 PDB 目录树，已作废），改用 OMF：
#     DATAFILE/TEMPFILE 只写 '+DATA'，Oracle 自动落 PDB 的 GUID 目录，无需关心 GUID
#   ⇒ 前置条件：db_create_file_dest = '+DATA' 且容器 = HDTPDB
#     （脚本 Step 0 断言强校验，不满足即 EXIT；换集群执行 MUST 重新核实）
#   ⇒ 手写路径方案作废记录：原占位 '+RACDG/itp/' 来自老 ITP 测试库磁盘组，NEVER 回退

# 2.1 口令已按推荐值内联进本地脚本 CREATE USER 语句（双引号定界；本目录 svn:ignore，不入 VCS）
#   DBA 审核/可替换；替换时 MUST 避 & （sqlplus 对任何语句都做替换展开）和 " （口令定界符）

# 2.2 用 SYSDBA 连 hditpdb PDB 执行（NEVER 连 CDB 根）
sqlplus sys/<sys_pwd>@//172.120.110.25:1521/hditpdb as sysdba @docs/ops/environments/scripts/migration/20261014_bundle_m1_infra.sql

# 2.3 自检输出：8 张 TS + 5 个用户 + 1 个角色 + 同义词 全绿
#     （Step 5/6 的 GRANT 预期报 ORA-00942——业务表要等 M3 迁入，M3 后 MUST 重跑这两步）
# 2.4 把 4 个 schema 口令安全分发到各模块 K8s secret + application.properties
```

**风险控制**：
- ⚠️ 本步骤只做创建，不删不改任何现有对象，安全
- ⚠️ 口令不入 VCS，落地 K8s secret
- ⚠️ +DATA 磁盘组需有至少 4GB 余量（4 张 TS 初始 4.5GB，grid 用户 `asmcmd lsdg` 查）
- ⚠️ **建错位置/路径需清理时**：用 `20261017_cleanup_m1_wrong_objects.sql`（带安全闸：有数据即中止 + 人工确认闸），NEVER 手写裸 DROP；建在 CDB 根的就用 SYS 连根跑该脚本

---

### Step 2.5：分域 Schema 建表（`20261017_schema_*.sql`）

> **DDL 全部来自本仓库存量 SQL，NEVER 从老库 GET_DDL**（防老库漂移；老库只做数据导出 + 结构 diff）。由 `20261017_schema_gen.py` 从 46 个 DDL 源文件按域清单拆分生成，可重复生成。

```bash
# 顺序 MUST：PLAT → USER → RIDE → TXN（跨域外键/同义词依赖）
sqlplus QDITP_PLAT/<pwd>@//172.120.110.25:1521/hditpdb @docs/ops/environments/scripts/migration/20261017_schema_plat.sql
sqlplus QDITP_USER/<pwd>@//172.120.110.25:1521/hditpdb @docs/ops/environments/scripts/migration/20261017_schema_user.sql
sqlplus QDITP_RIDE/<pwd>@//172.120.110.25:1521/hditpdb @docs/ops/environments/scripts/migration/20261017_schema_ride.sql
sqlplus QDITP_TXN/<pwd>@//172.120.110.25:1521/hditpdb @docs/ops/environments/scripts/migration/20261017_schema_txn.sql
```

**覆盖与口径**（详见 `20261017_schema_manifest.md`）：
- ✅ 域清单 **123/123 张全部有本地 DDL**（PLAT 71 / USER 13 / RIDE 3 / TXN 36）+ 24 个序列按域归位；外键依赖已拓扑排序（如 RECON_BATCH 先于子表）
- ✅ 方言已校验：只收 Oracle 方言（`ry_20250522.sql` / `quartz.sql` 两个 MySQL 原版 NEVER 入池，用 `_oracle` 版）
- ⚠️ 4 张表 DDL 仅在 migration 文件（QRCODE_SUPPLEMENT_REQUEST / ALIPAY_PAY_CALLBACK_LOG / DAILY_TICKET_USAGE_LOG / GATE_RETRY_QUEUE）——已选入，注意与后续 migration 的关系
- ⚠️ 10 张本地有 DDL 但不在域清单（5 张 SYS_* / QRTZ_SIMPROP_TRIGGERS / AGM_DEVICE_STATUS / GATEWAY_ROUTE_CONFIG×2 / STATION_INFO），归属建议见 manifest，**MUST 人工裁决后决定是否补入 PLAT**
- ⚠️ 无 `TABLESPACE` 子句 ⇒ MUST 以域用户执行（落 M1 默认表空间）；NEVER 用 SYS
- ⚠️ 分区表分区上界以本地 DDL 为准；若仍到 `P202612`，灌数后 MUST 按 M0 补分区

---

### Step 3：M2/M3/M4 数据导出

> 🔴 **2026-10-17 起实际场景是跨库**（源 = 老 ITP `172.20.222.3:1521/AFCITPDB` → 目标 = 红岛生产 `172.120.110.25:1521/hditpdb`）。同库设计的 `INSERT INTO QDITP_TXN.x SELECT * FROM QDITP.x` 跨库不成立，**MUST 走 `20261017_m2m3_crossdb_hongdao.sql`**（expdp → 传输 → impdp REMAP_SCHEMA）；本节命令仅为该脚本 Phase 2 的展开，`20261014_bundle_m2_m3_migration.sql` 与 4 个 `import_qditp_*.sql` 已标作废（同库设计）。

**目标**：用 expdp 把 129 张表的数据导出成 4 个 dump 文件。

**操作**：
```bash
# 3.1 确认 DATA_PUMP_DIR 已创建且有写权限
# 3.2 PLAT 先导（其他域依赖 PLAT 的同义词）
expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
  directory=DATA_PUMP_DIR dumpfile=qditp_plat.dmp logfile=qditp_plat_export.log \
  tables=QDITP.BLACKLIST,QDITP.TBL_STATION_INFO,... \
  parallel=8 compression=all \
  exclude=grant,statistics \
  job_name=exp_plat_domain

# 3.3 USER / RIDE / TXN 并行导出
# （参考 export_qditp_{user,ride,txn}.sql 里的完整命令）

# 3.4 导出后验证
ls -lh /data/dump/qditp_*/qditp_*.dmp
tail -50 /data/dump/qditp_*/qditp_*_export.log
# 期望末尾有 "MASTER TABLE ... SUCCESSFULLY LOADED/UNLOADED"
```

**风险控制**：
- ⚠️ expdp 是只读操作，不影响生产
- ⚠️ `exclude=grant,statistics`（2026-10-17 起）：grant 由分库设计显式重授（M1 Step 5/6/7），statistics 导后重新收集；索引/约束/触发器随行导入，F2F GLOBAL 唯一索引保持现状（GLOBAL→LOCAL 是 M0 裁定项，单独处理）
- ⚠️ parallel=8 + compression=all 加速大数据量表导出
- ⚠️ 导出前行数基线已打印到日志，导入后可比对（跨库比对方法见 20261017_m2m3_crossdb_hongdao.sql Phase 1.5/6.2）
- ⚠️ 源库在线写入：导出窗口 MUST 与业务停写窗口对齐，TXN 域 recon/face-pay/web-admin 定时任务双跑硬约束照旧

---

### Step 4：M2/M3/M4 数据导入

> 🔴 **跨库场景以 `20261017_m2m3_crossdb_hongdao.sql` Phase 5 为准**：impdp 连接串 MUST 指向**目标库** `172.120.110.25:1521/hditpdb`（PDB，SYS AS SYSDBA），`remap_tablespace` 的源端表空间名 MUST 先从源库查实（不再是写死的 USERS）。本节为同库设计存档。

**目标**：用 impdp 把 dump 文件导入到对应 schema。

**操作**：
```bash
# 4.1 PLAT 先导入（跨库 = 连目标 PDB；remap_tablespace 源端以源库实际 TS 为准）
impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
  directory=hongdao_dp_dir dumpfile=qditp_plat.dmp logfile=qditp_plat_import.log \
  remap_schema=QDITP:QDITP_PLAT remap_tablespace=<SRC_TS>:ITP_PLAT_TS \
  table_exists_action=replace parallel=8 job_name=imp_plat_domain

# 4.2 导入后建跨库同义词（参见 create_split_grants_synonyms.sql）
# 4.3 USER / RIDE / TXN 并行导入
# （TXN 域导入期 MUST 停 recon/face-pay/web-admin 定时任务，双跑硬约束）

# 4.4 导入后行数比对（跨库 = 源侧基线 vs 目标侧计数，见跨库脚本 Phase 1.5/6.2）
```

**风险控制**：
- 🔴 **TXN 域导入期 MUST 停相关定时任务**（recon/face-pay/web-admin），否则双跑重复扣费
- 🔴 索引/约束/触发器本版随行导入（2026-10-17 起 exclude 只排 grant,statistics）；F2F GLOBAL 唯一索引保持现状，GLOBAL→LOCAL 是 M0 裁定项单独处理
- 🔴 `table_exists_action=replace` 会 drop + recreate 目标表，适合重跑但不适合增量
- ✅ PLAT 先导，其他域依赖 PLAT 的同义词，顺序不能乱
- ✅ 导入后 MUST 校准序列（Data Pump 不保留序列当前值）与重跑 M1 Step 5/6/7 授权

---

### Step 5：M4 全量校验

**目标**：确认所有表数据一致、索引有效、同义词可解析、分区补齐。

**操作**：
```bash
# 5.1 行数比对（每张表 src vs dst）
# （参考 bundle_m4_verify_rollback.sql 里的比对 SQL）

# 5.2 索引状态校验（F2F_* GLOBAL 索引应为 VALID）
SELECT index_name, status FROM user_indexes
WHERE table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT');

# 5.3 分区补齐校验（每张分区表 2027 分区应为 12 行）
SELECT table_name, COUNT(*) AS partition_count
FROM   user_tab_partitions
WHERE  table_name IN (...) AND partition_name LIKE 'P2027%'
GROUP BY table_name;

# 5.4 同义词解析校验（dba_synonyms 应全绿）
SELECT owner, synonym_name, table_owner, table_name, status
FROM   dba_synonyms
WHERE  owner IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','itp_ro');

# 5.5 切 datasource：各模块 application.properties 指向新 schema
# （参考手册附录 D 切换清单）

# 5.6 重启应用 + 观察 30 分钟 + 跑一笔真实交易验证
```

**风险控制**：
- ⚠️ 行数不一致 MUST 回滚（执行 Step 6 回滚预案）
- ⚠️ 索引 UNUSABLE MUST 重建后再切流量
- ⚠️ 同义词解析失败 MUST 检查 GRANT + 源表是否存在
- ⚠️ 切 datasource 后 MUST 观察 30 分钟再放行生产流量

---

### Step 6：回滚预案（出异常时执行）

**目标**：分级回滚，最小化影响。

**操作**：
```bash
# 6.1 级 1：撤销跨库同义词 + GRANT（可逆，不影响数据）
# 6.2 级 2：收回 QDITP_MIGRATOR 角色
# 6.3 级 3：切回原 datasource（秒级回滚，源表 _PRE_SPLIT 保留）
# 6.4 级 4：兜底（删除新 schema + 表空间 + DATAFILES，仅 M4 失败且决定放弃时执行）

# 详见：docs/ops/environments/scripts/migration/20261014_rollback_split.sql
```

**回滚决策树**：
```
出异常？
  ├─ 同义词/授权问题 → 级 1 回滚（秒级）
  ├─ 迁移角色未收回 → 级 2 回滚
  ├─ 数据不一致/索引损坏 → 级 3 回滚（切回原 datasource）
  └─ M4 全量失败且决定放弃 → 级 4 兜底（删 schema + 表空间）
```

---

### Step 7：观察期 + 清理

**目标**：cooldown 7 天后确认无误，清理备份表。

**操作**：
```bash
# 7.1 观察 7 天，确认无异常
# 7.2 清理备份表（谨慎！）
ALTER TABLE QDITP.GATE_TXN_PAY_PRE_SPLIT RENAME TO QDITP.BAK_GATE_TXN_PAY_20261014;
-- 确认无误后择期 DROP
DROP TABLE QDITP.BAK_GATE_TXN_PAY_20261014 CASCADE CONSTRAINTS;
-- 其余表同理
```

**风险控制**：
- ⚠️ BAK_* / _PRE_SPLIT 表 MUST 保留至少 7 天
- ⚠️ DROP 前 MUST 再次行数比对确认一致
- ⚠️ DROP 操作不可回滚，MUST DBA 审批后执行

---

## 三、风险控制总览

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| F2F_* GLOBAL 索引 UNUSABLE | 🔴 P0 | M0 改 LOCAL 或全程 `UPDATE GLOBAL INDEXES` |
| 双跑重复扣费 | 🔴 P0 | TXN 域导入期停 recon/face-pay/web-admin 定时任务 |
| CTAS 剥分区 | 🔴 P0 | 主迁移用 `DBMS_METADATA.GET_DDL`，严禁 CTAS |
| 分区 2027 失效 | 🔴 P0 | M0 必须在 2026-12-15 前完成 |
| 容量不足 | 🟡 P1 | 逻辑分库不扩容，须配合 M0 磁盘均衡 |
| 跨库 JOIN 交易数据 | 🟡 P1 | 代码层禁止，DDL 层靠同义词 + 授权隔离 |
| 口令泄露 | 🟡 P1 | 不入 VCS，落 K8s secret，DBA 审核后替换 |
| 回滚失败 | 🟢 P2 | 4 级递进回滚，级 3 切 datasource 秒级可逆 |

---

## 四、脚本使用速查

| 阶段 | 脚本 | 用途 |
|---|---|---|
| M0 | `m0_partition_precheck.sql` | 分区表盘点 |
| M0 | `m0_generate_add_partition.sql` | 生成补建 DDL |
| M0 | `bundle_m0_partition_fix.sql` | 一键执行 M0 |
| M1 | `bundle_m1_infra.sql` | 建表空间 + schema + 授权 |
| M2/M3/M4 | `export_qditp_{user,txn,ride,plat}.sql` | expdp 导出 |
| M2/M3/M4 | `import_qditp_{user,txn,ride,plat}.sql` | impdp 导入 |
| M4 | `bundle_m4_verify_rollback.sql` | 校验 + 回滚预案 |
| 回滚 | `rollback_split.sql` | 4 级回滚 |
| 基线 | `migration_precheck.sql` | M0 前基线 |

### 4.1 目录内其他资源（不属于上述执行步骤）

| 子目录/文件 | 性质 | 说明 |
|---|---|---|
| `20261023_*.py` / `domain_map.py` | 域拆分工具 | 按域切分导出文件与 schema 的生成器链（apply/split_data/split_sche + 域映射表） |
| `20261017_schema_manifest.md` | 基线清单 | 生成基线的来源标注与统计（含「（已删）」注记，重跑生成器前 MUST 看） |
| `20261017_schema_{plat,user,txn,ride}.sql` | 分域建表基线 | 生成产物，含每段 DDL 的 `[来源]` 标注 |

> ⚠️ **原 `db-history/` 11 个历史 SQL 已全部删除**（2026-10-26 合理性审计，用户二次裁决）。
> 其中 6 个曾因「导出基线里没有、只在这些脚本里的 DDL」被保留（一度分入 `ddl-extra/` / `tools/` / `seed/`），
> 一并删除后：**这些 DDL 仍在生成基线里**（`schema_plat.sql` 105/177/200/723/1319/1322 行、
> `schema_user.sql` 305 行），但**重跑 `20261017_schema_gen.py` 不会再产出它们**——
> 涉及 METRO_AGM_KEY_VERSION / METRO_AGM_KEY_POOL / COM_DEVICE_SYN_KEY /
> TBL_NOTICE_APP_FAILURE_RECORD / USER_ITP_REG_INFO_BAK20260909 及 2 序列 2 索引。
> **重跑 MUST 手工补回或先确认不再需要**，清单见 `20261017_schema_manifest.md`。详见 `../README.md` §六。

---

## 五、联系人与审批流程

1. **开发团队**：本手册 + 脚本起草
2. **DBA 审核**：§九 待确认项（口令 + 路径 + GLOBAL→LOCAL 裁定）
3. **运维审批**：Step 3/4 导出导入窗口 + Step 5 切流量窗口
4. **业务方确认**：Step 4 TXN 域导入期停定时任务（recon/face-pay/web-admin）
5. **甲方验收**：Step 5 全量校验通过 + Step 7 观察期 7 天无误

---

**文档版本**：v1.0（2026-10-14）
**最后更新**：2026-10-14
**下一步**：提交 DBA 审批 → 按 Step 1–7 执行 → 观察期 7 天 → 闭环
