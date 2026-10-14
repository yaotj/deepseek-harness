-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_RIDE 域数据导出脚本（expdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（仅供评审，严禁直接在生产库执行）
-- 用途：用 Data Pump 导出 QDITP_RIDE 域 3 张实体表 + STATION_INFO 同义词说明
-- 特殊说明：
--   - STATION_INFO 不迁实体表，改为指向 PLAT.TBL_STATION_INFO 的同义词
--   - QRCODE_TXN_DETAIL 为分区表 + 容量热点，导出时保留分区定义
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE EXP_DIR    = 'DATA_PUMP_DIR'
DEFINE OUT_DIR    = '/data/dump/qditp_ride'
DEFINE DUMPFILE   = 'qditp_ride.dmp'
DEFINE LOGFILE    = 'qditp_ride_export.log'
DEFINE SCHEMA     = 'QDITP'

PROMPT ============================================================================
PROMPT QDITP_RIDE 域数据导出命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&EXP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   tables=&SCHEMA..QRCODE_STATUS,&SCHEMA..QRCODE_TXN_DETAIL,&SCHEMA..QRCODE_SUPPLEMENT_REQUEST \
PROMPT   parallel=4 \
PROMPT   compression=all \
PROMPT   exclude=index,constraint,trigger,grant,statistics \
PROMPT   job_name=exp_ride_domain
PROMPT ============================================================================

PROMPT === 导出前行数基线 ===
BEGIN
  FOR t IN (
    SELECT 'QRCODE_STATUS'           n FROM dual UNION ALL
    SELECT 'QRCODE_TXN_DETAIL'       FROM dual UNION ALL
    SELECT 'QRCODE_SUPPLEMENT_REQUEST' FROM dual
  ) LOOP
    BEGIN
      DECLARE v_cnt NUMBER;
      BEGIN
        EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || '&SCHEMA.' || t.n INTO v_cnt;
        DBMS_OUTPUT.PUT_LINE(t.n || ': ' || v_cnt || ' 行');
      END;
    EXCEPTION WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE(t.n || ': [无法计数] ' || SQLERRM);
    END;
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('-- STATION_INFO 为同义词，指向 QDITP_PLAT.TBL_STATION_INFO，无需导出');
END;
/

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 把 dump 文件拷贝到目标机器
PROMPT 2. 执行 import_qditp_ride.sql
PROMPT 3. 导入后建 STATION_INFO 同义词（指向 PLAT.TBL_STATION_INFO）