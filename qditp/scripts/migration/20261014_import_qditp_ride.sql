-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_RIDE 域数据导入脚本（impdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（⚠️ 同库设计；**跨库场景（红岛生产）以 20261017_m2m3_crossdb_hongdao.sql
--       为准**——本文件 impdp 连接串指向老环境、行数比对为同库假设，NEVER 直接用于红岛）
-- 用途：用 Data Pump 导入 qditp_ride.dmp 到 QDITP_RIDE schema
-- 特殊说明：
--   - STATION_INFO 不导入实体表，导入后建同义词指向 PLAT.TBL_STATION_INFO
--   - QRCODE_TXN_DETAIL 为分区表 + 容量热点，导入后优先排归档
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE IMP_DIR    = 'DATA_PUMP_DIR'
DEFINE DUMPFILE   = 'qditp_ride.dmp'
DEFINE LOGFILE    = 'qditp_ride_import.log'
DEFINE SRC_SCHEMA = 'QDITP'
DEFINE TGT_SCHEMA = 'QDITP_RIDE'

PROMPT ============================================================================
PROMPT QDITP_RIDE 域数据导入命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT impdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&IMP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   remap_schema=&SRC_SCHEMA.:&TGT_SCHEMA. \
PROMPT   remap_tablespace=USERS:ITP_RIDE_TS \
PROMPT   table_exists_action=replace \
PROMPT   parallel=4 \
PROMPT   job_name=imp_ride_domain
PROMPT ============================================================================

PROMPT === 导入后行数比对 ===
BEGIN
  FOR t IN (
    SELECT 'QRCODE_STATUS'           n FROM dual UNION ALL
    SELECT 'QRCODE_TXN_DETAIL'       FROM dual UNION ALL
    SELECT 'QRCODE_SUPPLEMENT_REQUEST' FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM ' || '&SRC_SCHEMA..' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM ' || '&TGT_SCHEMA..' || t.n || ') dst FROM dual;'
    );
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('-- STATION_INFO 为同义词，指向 QDITP_PLAT.TBL_STATION_INFO，无需比对');
END;
/

PROMPT === 导入后建 STATION_INFO 同义词（技术债收敛）===
PROMPT -- 在 QDITP_RIDE schema 下执行：
PROMPT GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_RIDE;
PROMPT CREATE SYNONYM QDITP_RIDE.STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 核对行数比对结果
PROMPT 2. 建 STATION_INFO 同义词
PROMPT 3. 继续导入其他域（USER / TXN / PLAT）