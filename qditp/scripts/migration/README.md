# 青岛地铁 ITP 数据库按域分库 — 迁移执行指南

**配套文档**：`docs/ops/数据库按域分库迁移手册.md`
**配套脚本**：`scripts/migration/` 目录（16 个 SQL 文件）
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
sqlplus qditp/qditp@//172.20.222.3:1521/AFCITPDB @scripts/migration/20261014_m0_partition_precheck.sql

# 1.2 生成补建 DDL（每表一个 .sql 文件）
sqlplus qditp/qditp@//172.20.222.3:1521/AFCITPDB @scripts/migration/20261014_m0_generate_add_partition.sql
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
# 2.1 DBA 替换路径前缀 + 审核/替换 5 个口令
#   - +RACDG/itp/ → 真实 ASM 磁盘组路径
#   - 5 个口令见手册 §九.1（草案，DBA 可改）

# 2.2 用 SYSDBA 执行
sqlplus / as sysdba @scripts/migration/20261014_bundle_m1_infra.sql

# 2.3 自检输出：8 张 TS + 5 个用户 + 1 个角色 + 同义词 全绿
# 2.4 把 4 个 schema 口令安全分发到各模块 K8s secret + application.properties
```

**风险控制**：
- ⚠️ 本步骤只做创建，不删不改任何现有对象，安全
- ⚠️ 口令不入 VCS，落地 K8s secret
- ⚠️ RACDG 磁盘组需有至少 4GB 余量（4 张 TS 初始 4.5GB）

---

### Step 3：M2/M3/M4 数据导出

**目标**：用 expdp 把 129 张表的数据导出成 4 个 dump 文件。

**操作**：
```bash
# 3.1 确认 DATA_PUMP_DIR 已创建且有写权限
# 3.2 PLAT 先导（其他域依赖 PLAT 的同义词）
expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
  directory=DATA_PUMP_DIR dumpfile=qditp_plat.dmp logfile=qditp_plat_export.log \
  tables=QDITP.BLACKLIST,QDITP.TBL_STATION_INFO,... \
  parallel=8 compression=all \
  exclude=index,constraint,trigger,grant,statistics \
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
- ⚠️ `exclude=index,constraint,trigger,grant,statistics` 避免依赖源 schema 的索引定义
- ⚠️ parallel=8 + compression=all 加速大数据量表导出
- ⚠️ 导出前行数基线已打印到日志，导入后可比对

---

### Step 4：M2/M3/M4 数据导入

**目标**：用 impdp 把 dump 文件导入到对应 schema。

**操作**：
```bash
# 4.1 PLAT 先导入
impdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
  directory=DATA_PUMP_DIR dumpfile=qditp_plat.dmp logfile=qditp_plat_import.log \
  remap_schema=QDITP:QDITP_PLAT remap_tablespace=USERS:ITP_PLAT_TS \
  table_exists_action=replace parallel=8 job_name=imp_plat_domain

# 4.2 导入后建跨库同义词（参见 create_split_grants_synonyms.sql）
# 4.3 USER / RIDE / TXN 并行导入
# （TXN 域导入期 MUST 停 recon/face-pay/web-admin 定时任务，双跑硬约束）

# 4.4 导入后行数比对
# （参考 import_qditp_{user,ride,txn,plat}.sql 里的比对 SQL）
```

**风险控制**：
- 🔴 **TXN 域导入期 MUST 停相关定时任务**（recon/face-pay/web-admin），否则双跑重复扣费
- 🔴 F2F_ORDER/PAYMENT/RESULT_REPORT 的 GLOBAL 唯一索引需手动重建（exclude 掉）
- 🔴 `table_exists_action=replace` 会 drop + recreate 目标表，适合重跑但不适合增量
- ✅ PLAT 先导，其他域依赖 PLAT 的同义词，顺序不能乱

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

# 详见：scripts/migration/20261014_rollback_split.sql
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
