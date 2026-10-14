-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_PLAT 域数据导入脚本（impdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（仅供评审，严禁直接在生产库执行）
-- 用途：用 Data Pump 导入 qditp_plat.dmp 到 QDITP_PLAT schema
-- 特殊说明：
--   - SYS_* / QRTZ_* 导入后 web-server 的 datasource 指向 QDITP_PLAT
--   - BLACKLIST* / TBL_STATION_INFO 等公共引用表导入后需对 USER/TXN/RIDE 建同义词
--   - 导入顺序：PLAT 先导（其他域依赖 PLAT 的同义词）
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE IMP_DIR    = 'DATA_PUMP_DIR'
DEFINE DUMPFILE   = 'qditp_plat.dmp'
DEFINE LOGFILE    = 'qditp_plat_import.log'
DEFINE SRC_SCHEMA = 'QDITP'
DEFINE TGT_SCHEMA = 'QDITP_PLAT'

PROMPT ============================================================================
PROMpt QDITP_PLAT 域数据导入命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT impdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&IMP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   remap_schema=&SRC_SCHEMA.:&TGT_SCHEMA. \
PROMPT   remap_tablespace=USERS:ITP_PLAT_TS \
PROMPT   table_exists_action=replace \
PROMPT   parallel=8 \
PROMPT   job_name=imp_plat_domain
PROMPT ============================================================================

PROMPT === 导入后行数比对（抽样关键表）===
BEGIN
  FOR t IN (
    SELECT 'BLACKLIST'             n FROM dual UNION ALL
    SELECT 'TBL_STATION_INFO'      FROM dual UNION ALL
    SELECT 'TBL_LINE_INFO'         FROM dual UNION ALL
    SELECT 'SYS_USER'              FROM dual UNION ALL
    SELECT 'SYS_JOB'               FROM dual UNION ALL
    SELECT 'QRTZ_JOB_DETAILS'      FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM ' || '&SRC_SCHEMA..' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM ' || '&TGT_SCHEMA..' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT === 导入后建跨库同义词（PLAT 公共引用表）===
PROMPT -- 在 QDITP_USER 下执行：
PROMPT GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_USER;
PROMPT CREATE SYNONYM QDITP_USER.BLACKLIST FOR QDITP_PLAT.BLACKLIST;
PROMPT -- ... 其余同义词见 create_split_grants_synonyms.sql
PROMPT
PROMPT -- 在 QDITP_TXN 下执行：
PROMPT GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_TXN;
PROMPT CREATE SYNONYM QDITP_TXN.BLACKLIST FOR QDITP_PLAT.BLACKLIST;
PROMPT
PROMPT -- 在 QDITP_RIDE 下执行：
PROMPT GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_RIDE;
PROMPT CREATE SYNONYM QDITP_RIDE.STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;
PROMPT GRANT SELECT ON QDITP_PLAT.BLACKLIST TO QDITP_RIDE;
PROMPT CREATE SYNONYM QDITP_RIDE.BLACKLIST FOR QDITP_PLAT.BLACKLIST;

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 核对行数比对结果
PROMPT 2. 建跨库同义词（USER/TXN/RIDE 三域）
PROMPT 3. 切换 web-server 的 datasource 到 QDITP_PLAT
PROMPT 4. 继续导入其他域（USER / TXN / RIDE）