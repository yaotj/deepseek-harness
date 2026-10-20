-- =============================================================================
-- 青岛地铁 ITP 分库迁移 — M2/M3 跨库迁移执行脚本（老环境 → 红岛生产 HDTPDB）
-- 场景：源 = 老 ITP 业务库 172.20.222.3:1521/AFCITPDB（schema QDITP，在线业务数据）
--       目标 = 红岛生产 172.120.110.25:1521/hditpdb（PDB，4 个新 schema + 4 TS，M1 已建）
-- 状态：v1.0 · 2026-10-17 · 配套：20261014_bundle_m1_infra.sql（M1，已完成）
--
-- 🔴 与同库设计（20261014_bundle_m2_m3_migration.sql）的本质区别：
--   同库设计的核心动作 `INSERT INTO QDITP_TXN.x SELECT * FROM QDITP.x` 在跨库下
--   **完全不成立**（无 DB Link 时跨库不可见）；本脚本改走 expdp 导出 → 文件传输 →
--   impdp REMAP_SCHEMA 导入，DB Link 方案（手动 §九.1）未采用，NEVER 混用两条路径。
--
-- 🔴 生产纪律（MUST）：
--   1. 源库是在线业务库，expdp/impdp 全程有审计 + 堡垒机留痕，MUST 人工复核后执行；
--   2. 源库数据持续写入 ⇒ 导出窗口与业务停写窗口 MUST 提前与业务方确认
--      （TXN 域 recon / face-pay / web-admin 定时任务双跑硬约束照旧）；
--   3. 本脚本只生成命令与校验语句，真正执行的是 expdp/impdp（服务器 shell）与
--      其后的 SQL，每步输出 MUST 留档。
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============================================================================
PROMPT Phase 0：前置确认（逐项打勾再往下）
PROMPT ============================================================================
PROMPT   0.1 M1 已完成：4 schema（QDITP_USER/TXN/RIDE/PLAT）+ 8 TS/TEMP + itp_ro 在 hditpdb PDB 内
PROMPT   0.2 两库网络：本机到 172.120.110.25:1521 与 172.20.222.3:1521 均通（Test-NetConnection）
PROMPT   0.3 dump 传输路径已定：源库服务器 → 红岛库服务器（scp 跳板 / FTP / 共享存储，
PROMPT       两库在不同网段，NEVER 假设直连）；红岛侧 OS 目录已建且 oracle 用户可读写
PROMPT   0.4 业务停写窗口已批（导出 + 导入 + 校验全窗口）；TXN 域定时任务已停
PROMPT   0.5 源库 QDITP 的 M0（分区补到 2027 / F2F GLOBAL→LOCAL 裁定）已决：先修再导，
PROMPT       或导后在新 PDB 上修（6.5 会再检查）
PROMPT   0.6 回滚预案：源库全程只读不碰；目标库清空重来 = 重跑清理脚本
PROMPT       （20261017_cleanup_m1_wrong_objects.sql 同款白名单 + DROP ... AND DATAFILES）
PROMPT ============================================================================

-- =============================================================================
-- Phase 1：源库侧信息采集（连老环境 172.20.222.3:1521/AFCITPDB，以 DBA 执行）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 1：源库信息采集（决定 impdp 的 remap_tablespace 与序列校准）
PROMPT ============================================================================

PROMPT === 1.1 源库字符集（目标 ZHS16GBK，impdp 自动转换，记下来备用）===
SELECT * FROM nls_database_parameters WHERE parameter IN ('NLS_CHARACTERSETNAME','NLS_NCHAR_CHARACTERSETNAME');

PROMPT === 1.2 源表空间清单（remap_tablespace 的源端，表+索引+LOB 都要覆盖）===
SELECT DISTINCT tablespace_name FROM dba_tables  WHERE owner = 'QDITP' ORDER BY 1;
SELECT DISTINCT tablespace_name FROM dba_indexes WHERE owner = 'QDITP' ORDER BY 1;
-- LOB 段（如有独立 LOB TS，一并加入 remap_tablespace 列表）：
SELECT DISTINCT tablespace_name FROM dba_lobs WHERE owner = 'QDITP' ORDER BY 1;

PROMPT === 1.3 源序列清单 + 当前值（生成目标侧校准语句，纯 SQL 无变量）===
-- 把输出存下来，6.1 在目标侧照跑（schema 名已按域替换）
SELECT 'ALTER SEQUENCE QDITP_' || '{USER,TXN,RIDE,PLAT}' || '.' || sequence_name ||
       ' RESTART START WITH ' || last_number || ';'
FROM   dba_sequences
WHERE  sequence_owner = 'QDITP'
ORDER  BY sequence_name;
-- 说明：last_number 为源库下一个待分配值（uncached 口径）；cache>1 时可能略大于实际
-- 已用最大值，偏大安全（跳号），偏小会主键冲突，NEVER 手工改小。

PROMPT === 1.4 源 schema 非表对象盘点（确认 impdp 覆盖面）===
SELECT object_type, COUNT(*) FROM dba_objects WHERE owner = 'QDITP'
GROUP BY object_type ORDER BY object_type;
-- 重点核对：SEQUENCE / TRIGGER / VIEW / PROCEDURE / PACKAGE / DATABASE LINK
-- DATABASE LINK 若指向老环境地址，导入后 MUST 删除（分库设计默认不用 DB Link）

PROMPT === 1.5 行数基线（源库执行，输出存盘；6.2 与目标侧比对）===
-- 方式：直接跑各域导出脚本内置的计数块（表清单唯一事实源），以 QDITP 身份执行并 SPOOL：
--   SQL> SPOOL baseline_user.txt
--   SQL> @20261014_export_qditp_user.sql      -- 其中 expdp 行只是 PROMPT 不回放
--   SQL> SPOOL OFF
-- 四个域（plat/user/ride/txn）各来一遍；目标侧同法以 QDITP_<域> 身份再跑，diff 两份文件。
PROMPT   （详见 Phase 6.2）

-- =============================================================================
-- Phase 2：源库侧导出（在源库服务器 shell 执行；表清单见各 export 脚本，勿抄错）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 2：expdp 导出（源库服务器 shell；连接串 = 老环境）
PROMPT   表清单以 20261014_export_qditp_<域>.sql 内 tables= 为唯一事实源
PROMPT   本版 exclude 只排 grant,statistics —— 索引/约束/触发器随行导入（见下方说明）
PROMPT ============================================================================

PROMPT --- PLAT（先导，其他域的同义词依赖它）---
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=DATA_PUMP_DIR dumpfile=qditp_plat.dmp logfile=qditp_plat_export.log \
PROMPT   tables=QDITP.BLACKLIST,QDITP.TBL_STATION_INFO,...（完整清单见 export_qditp_plat.sql）\
PROMPT   parallel=8 compression=all \
PROMPT   exclude=grant,statistics \
PROMPT   job_name=exp_plat_domain

PROMPT --- USER / RIDE / TXN（TXN 数据量最大，放业务低峰；可并行但注意 IO）---
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=DATA_PUMP_DIR dumpfile=qditp_user.dmp logfile=qditp_user_export.log \
PROMPT   tables=...（见 export_qditp_user.sql）parallel=4 compression=all \
PROMPT   exclude=grant,statistics job_name=exp_user_domain
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=DATA_PUMP_DIR dumpfile=qditp_ride.dmp logfile=qditp_ride_export.log \
PROMPT   tables=QDITP.QRCODE_STATUS,QDITP.QRCODE_TXN_DETAIL,QDITP.QRCODE_SUPPLEMENT_REQUEST \
PROMPT   parallel=4 compression=all exclude=grant,statistics job_name=exp_ride_domain
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=DATA_PUMP_DIR dumpfile=qditp_txn.dmp logfile=qditp_txn_export.log \
PROMPT   tables=...（见 export_qditp_txn.sql）parallel=8 compression=all \
PROMPT   exclude=grant,statistics job_name=exp_txn_domain

PROMPT --- 序列（schema 级导出，与表分开；domain 归属不重要，全量导入）---
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=DATA_PUMP_DIR dumpfile=qditp_seq.dmp logfile=qditp_seq_export.log \
PROMPT   schemas=QDITP include=SEQUENCE job_name=exp_qditp_seq

PROMPT === 导出后验证（每个 dump 必查）===
PROMPT   ls -lh $DATA_PUMP_DIR/qditp_*.dmp
PROMPT   tail -50 qditp_*_export.log    # 期望末尾 MASTER TABLE ... SUCCESSFULLY LOADED/UNLOADED
PROMPT   # 在线库一致性兜底（可选，undo 保留够时可用，失败会 ORA-01555）：
PROMPT   #   给 2.1~2.4 统一加 flashback_time="to_timestamp('YYYY-MM-DD HH24:MI:SS')"
PROMPT   # 注意：F2F_* 的 GLOBAL 唯一索引本版随行导入（保持现状）；
PROMPT   #       GLOBAL→LOCAL 是 M0 裁定项，导入后单独处理，NEVER 在导出侧改写。

-- =============================================================================
-- Phase 3：传输（两库不同网段，路径以运维安排为准）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 3：dump 传输（源库服务器 → 红岛库服务器）
PROMPT   4 个域 dump + qditp_seq.dmp + 各自 log 一并传；传后 md5sum 比对：
PROMPT     源：md5sum qditp_*.dmp > dumps.md5
PROMPT     目标：md5sum -c dumps.md5
PROMPT   ⚠️ 小表可容忍直接传；TXN 域 dump 可能较大，预留磁盘空间（asmcmd lsdg 看的是
PROMPT      ASM 组，OS 文件系统另看 df -h）
PROMPT ============================================================================

-- =============================================================================
-- Phase 4：目标侧准备（连红岛 hditpdb PDB，以 SYS 执行）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 4：目标侧 DIRECTORY（在 hditpdb PDB 内执行）
PROMPT ============================================================================

-- OS 侧先建目录并授权 oracle 用户（DBA 在服务器执行）：
--   mkdir -p /data/dump/qditp && chown oracle:oinstall /data/dump/qditp

CREATE OR REPLACE DIRECTORY hongdao_dp_dir AS '/data/dump/qditp';

PROMPT === 4.1 确认 4 个目标用户存在且默认表空间正确（M1 产物）===
SELECT username, default_tablespace, temporary_tablespace
FROM   dba_users
WHERE  username IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','QDITP_PLAT','ITP_RO')
ORDER  BY username;

-- =============================================================================
-- Phase 5：目标侧导入（红岛服务器 shell 执行；连接串 = hditpdb PDB）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 5：impdp 导入（红岛服务器 shell；SYS AS SYSDBA 连 PDB 服务名）
PROMPT   <SRC_TS_*> = Phase 1.2 查出的源表空间名（有几个写几个，全部映射到本域 TS）
PROMPT   顺序 MUST：PLAT → USER → RIDE → TXN（同义词依赖 PLAT 先落地）
PROMPT ============================================================================

PROMPT --- PLAT ---
PROMPT impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
PROMPT   directory=hongdao_dp_dir dumpfile=qditp_plat.dmp logfile=qditp_plat_import.log \
PROMPT   remap_schema=QDITP:QDITP_PLAT \
PROMPT   remap_tablespace=<SRC_TS_1>:ITP_PLAT_TS,<SRC_TS_2>:ITP_PLAT_TS \
PROMPT   table_exists_action=replace parallel=8 job_name=imp_plat_domain

PROMPT --- USER ---
PROMPT impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
PROMPT   directory=hongdao_dp_dir dumpfile=qditp_user.dmp logfile=qditp_user_import.log \
PROMPT   remap_schema=QDITP:QDITP_USER \
PROMPT   remap_tablespace=<SRC_TS_1>:ITP_USER_TS,<SRC_TS_2>:ITP_USER_TS \
PROMPT   table_exists_action=replace parallel=4 job_name=imp_user_domain

PROMPT --- RIDE ---
PROMPT impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
PROMPT   directory=hongdao_dp_dir dumpfile=qditp_ride.dmp logfile=qditp_ride_import.log \
PROMPT   remap_schema=QDITP:QDITP_RIDE \
PROMPT   remap_tablespace=<SRC_TS_1>:ITP_RIDE_TS,<SRC_TS_2>:ITP_RIDE_TS \
PROMPT   table_exists_action=replace parallel=4 job_name=imp_ride_domain

PROMPT --- TXN（数据量最大，低峰执行；导入前确认定时任务已停）---
PROMPT impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
PROMPT   directory=hongdao_dp_dir dumpfile=qditp_txn.dmp logfile=qditp_txn_import.log \
PROMPT   remap_schema=QDITP:QDITP_TXN \
PROMPT   remap_tablespace=<SRC_TS_1>:ITP_TXN_TS,<SRC_TS_2>:ITP_TXN_TS \
PROMPT   table_exists_action=replace parallel=8 job_name=imp_txn_domain

PROMPT --- 序列 ---
PROMPT impdp '"sys/<sys_pwd>@//172.120.110.25:1521/hditpdb" AS SYSDBA' \
PROMPT   directory=hongdao_dp_dir dumpfile=qditp_seq.dmp logfile=qditp_seq_import.log \
PROMPT   remap_schema=QDITP:QDITP_PLAT \
PROMPT   table_exists_action=replace job_name=imp_qditp_seq
PROMPT   # 序列统一落 QDITP_PLAT（公共域），各业务 schema 用同义词或直接授权引用；
PROMPT   # 归属方案若定的是「序列跟表走」，导入后按 6.1 的 ALTER 语句逐条改 owner：
PROMPT   #   ALTER SEQUENCE QDITP_PLAT.SEQ_X RENAME TO QDITP_TXN.SEQ_X;（PDB 内跨 schema 可行）

PROMPT === 5.1 导入日志必查 ===
PROMPT   grep -i "error\|warning\|ORA-\|character set" qditp_*_import.log
PROMPT   # 字符集告警：AL32UTF8→ZHS16GBK 转换，个别生僻字可能变 ?，记录待业务确认
PROMPT   # DATABASE LINK：若日志出现 DB_LINK 对象，导入后 MUST DROP（指向老环境）

-- =============================================================================
-- Phase 6：导入后收尾（连红岛 hditpdb PDB，以 SYS 执行）
-- =============================================================================
PROMPT ============================================================================
PROMPT Phase 6：收尾校验与修复（顺序：序列 → 行数 → 统计 → 授权 → 分区 → 索引）
PROMPT ============================================================================

PROMPT === 6.1 序列校准（Phase 1.3 生成的 ALTER 语句原样跑，schema 按实际归属替换）===
-- ALTER SEQUENCE QDITP_PLAT.SEQ_X RESTART START WITH <last_number>;
-- 逐条执行后抽查：SELECT sequence_name, last_number FROM dba_sequences WHERE sequence_owner LIKE 'QDITP_%';

PROMPT === 6.2 行数比对（与 Phase 1.5 基线 diff）===
-- 目标侧同法重跑各域导出脚本的计数块，但以 QDITP_<域> 身份：
--   SQL> SPOOL count_user.txt
--   SQL> DEFINE SCHEMA=QDITP_USER
--   SQL> @20261014_export_qditp_user.sql
--   SQL> SPOOL OFF
-- 然后 diff baseline_user.txt count_user.txt；四域全过才算数。
-- ⚠️ 源库在线写入 ⇒ 基线之后新增的行不在 dump 里，差几行属正常，记录增量即可。

PROMPT === 6.3 统计信息（全 schema）===
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('QDITP_PLAT');
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('QDITP_USER');
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('QDITP_RIDE');
EXEC DBMS_STATS.GATHER_SCHEMA_STATS('QDITP_TXN');

PROMPT === 6.4 重跑 M1 授权（表已存在，Step 5/6/7 的 GRANT 这次会成功）===
-- 同义词在 M1 时已建成（可指向空对象），本次只需补 GRANT：
--   GRANT SELECT ON QDITP_USER.USER_ITP_REG_INFO TO itp_ro;
--   GRANT SELECT ON QDITP_PLAT.BLACKLIST, QDITP_PLAT.BLACKLIST_OPERATE_LOG,
--       QDITP_PLAT.BLACKLIST_RELEASED TO QDITP_USER;
--   GRANT SELECT ON QDITP_PLAT.BLACKLIST, QDITP_PLAT.BLACKLIST_OPERATE_LOG,
--       QDITP_PLAT.BLACKLIST_RELEASED TO QDITP_TXN;
--   GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_RIDE;
--   GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_USER, QDITP_RIDE;
--   GRANT SELECT ON QDITP_USER.APP_USER_PAY_CHANNEL TO QDITP_TXN;
-- （与 bundle_m1_infra.sql Step 5/6/7 逐条对应；重跑整段也行，同义词已存在的会报
--   ORA-00955，忽略即可）

PROMPT === 6.5 分区悬崖复查（导入的分区表仍只到 P202612？）===
SELECT table_name, partition_count
FROM   (SELECT table_name, COUNT(*) AS partition_count
        FROM   dba_tab_partitions
        WHERE  table_owner LIKE 'QDITP_%'
        GROUP  BY table_name)
WHERE  table_name IN ('GATE_TXN_PAY','QRCODE_TXN_DETAIL','ALIPAY_PAY_TXN_DETAIL',
                     'ALIPAY_REFUND_TXN_DETAIL','ALIPAY_PAY_CALLBACK_LOG',
                     'ALIPAY_PAY_CENTER_MSG_LOG','F2F_ORDER','F2F_PAYMENT',
                     'F2F_RESULT_REPORT','PAY_TXN_DETAIL')
ORDER  BY table_name;
-- 若 max(partition_position) 对应 P202612：MUST 在新 PDB 上执行 M0 补分区
-- （scripts/migration/20261014_m0_generate_add_partition.sql 同款思路，NEVER 拖过 2026-12-15）

PROMPT === 6.6 F2F GLOBAL 唯一索引确认（本版随行导入，保持 GLOBAL 原状）===
SELECT index_name, table_name, uniqueness, status
FROM   dba_indexes
WHERE  owner = 'QDITP_TXN' AND index_name LIKE 'UK_%' AND uniqueness = 'UNIQUE'
ORDER  BY table_name;
-- GLOBAL→LOCAL 是 M0 裁定项：改 LOCAL 必须把分区键纳入唯一键，否则唯一性失效；
-- 未裁定前保持 GLOBAL，NEVER 顺手改。

PROMPT === 6.7 基础数据核对与补种（三类，逐项过）===
-- 6.7.1 导出前（源库）确认仓库种子已在源库执行——没执行过的种子，数据不在 dump 里：
--   SELECT COUNT(*) FROM QDITP.COM_DEVICE_SYN_KEY;      -- 期望 > 0（20260807 AGM 密钥种子）
--   SELECT COUNT(*) FROM QDITP.METRO_AGM_KEY_POOL;      -- 期望 > 0
--   SELECT COUNT(*) FROM QDITP.METRO_AGM_KEY_VERSION;   -- 期望 > 0
--   SELECT job_id, invoke_target FROM QDITP.SYS_JOB ORDER BY job_id;  -- 期望 19 条，编号 200~345 步长 5
--   （上述表都在 PLAT 域导出清单内，源库有数据即随 dump 迁移，无需单独动作）
--
-- 6.7.2 导入后（目标库）重跑仓库种子——防源库落后于代码，幂等可重跑：
--   cd web-server/sql
--   sqlplus QDITP_PLAT/<pwd>@//172.120.110.25:1521/hditpdb @ry_20250522_oracle.sql
--   for f in *-menu.sql business-menu-permissions.sql page-menu-complete.sql \
--            sys-menu-route-name-migration.sql user-query-debit-detail-perm-migration.sql; do
--     sqlplus QDITP_PLAT/<pwd>@//172.120.110.25:1521/hditpdb @$f
--   done
--   （种子全部幂等：not exists 取号、NEVER 硬编码主键；不重跑的后果是综管台菜单缺失）
--
-- 6.7.3 导入后 MUST 核对（不是数据、是口径与缓存）：
--   a. sys_menu 行数 vs 源库（菜单丢了 = 综管台白屏/功能入口 404）
--   b. sys_config：RuoYi 进程内 LocalCache ⇒ 改库不生效，MUST 走控制台「参数设置→刷新缓存」或重启 web-admin
--   c. sys_job 19 条完整性 + 编号未漂移；web-admin 是 Quartz 内存 JobStore ⇒ 重启才加载新任务
--   d. LOGIC_CARD_POOL_CARD 的 AVAILABLE 卡数（生产售票直接依赖）；
--      生产环境 NEVER 清库回收卡号——那是测试专用操作，生产只用 ALTER 流程
--   e. TBL_STATION_INFO 读取口径 MUST 带 PARA_VER_NO = MAX(PARA_VER_NO)（para-server 事实）
--   f. COM_DEVICE_SYN_KEY / METRO_AGM_KEY_* 行数 > 0（AGM 密钥同步链路依赖，丢了 IF1A-02 挂）

PROMPT ============================================================================
PROMPT 完成判定：四域行数 diff 干净 + 序列抽查通过 + 6.3~6.6 全绿 ⇒ M2/M3 完成
PROMPT 下一步：M4 全量校验（20261014_bundle_m4_verify_rollback.sql）+ 业务侧联调
PROMPT ============================================================================
