-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_USER 域数据导入脚本（impdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（仅供评审，严禁直接在生产库执行）
-- 用途：用 Data Pump 导入 qditp_user.dmp 到 QDITP_USER schema
-- 执行前置：
--   1. 包 2（M1 基础建设）已完成（QDITP_USER schema + ITP_USER_TS 已创建）
--   2. dump 文件已拷贝到服务器 &IMP_DIR 路径下
--   3. 当前登录为 DBA 或 QDITP_USER
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE IMP_DIR    = 'DATA_PUMP_DIR'
DEFINE DUMPFILE   = 'qditp_user.dmp'
DEFINE LOGFILE    = 'qditp_user_import.log'
DEFINE SRC_SCHEMA = 'QDITP'
DEFINE TGT_SCHEMA = 'QDITP_USER'

PROMPT ============================================================================
PROMPT QDITP_USER 域数据导入命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT impdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&IMP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   remap_schema=&SRC_SCHEMA.:&TGT_SCHEMA. \
PROMPT   remap_tablespace=USERS:ITP_USER_TS \
PROMPT   table_exists_action=replace \
PROMPT   parallel=4 \
PROMPT   job_name=imp_user_domain
PROMPT ============================================================================

-- 或者用 SQL*Plus 方式（CREATE DIRECTORY + impdp 附加工具）
PROMPT --- 备选：在 SQL*Plus 里创建 DIRECTORY（若不存在）---
-- CREATE OR REPLACE DIRECTORY DATA_PUMP_DIR AS '/data/dump';
-- GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO QDITP_USER;

PROMPT === 导入后行数比对 ===
BEGIN
  FOR t IN (
    SELECT 'ACCOUNT_EXCEPTION_TICKET'         n FROM dual UNION ALL
    SELECT 'ALIPAY_PHONE_CHANGE_LOG'          FROM dual UNION ALL
    SELECT 'APP_USER_PAY_CHANNEL'             FROM dual UNION ALL
    SELECT 'USER_ACC_EMPLOYEE_CARD'           FROM dual UNION ALL
    SELECT 'USER_ACC_EMPLOYEE_CARD_LOG'       FROM dual UNION ALL
    SELECT 'USER_ACC_TICKETNO'                FROM dual UNION ALL
    SELECT 'USER_ITP_REG_INFO'                FROM dual UNION ALL
    SELECT 'USER_ITP_REG_INFO_BAK20260909'    FROM dual UNION ALL
    SELECT 'USER_ITP_REG_LOG'                 FROM dual UNION ALL
    SELECT 'USER_PHONE_CHANGE_LOG'            FROM dual UNION ALL
    SELECT 'LOGIC_CARD_POOL_BATCH'            FROM dual UNION ALL
    SELECT 'LOGIC_CARD_POOL_CARD'             FROM dual UNION ALL
    SELECT 'LOGIC_CARD_POOL_TYPE_LOCK'        FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM ' || '&SRC_SCHEMA..' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM ' || '&TGT_SCHEMA..' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT === 导入后重建索引/约束/序列 ===
PROMPT --- 以下 DDL 在 QDITP_USER schema 下执行 ---
PROMPT -- 1. 重建索引（原脚本 exclude=index，需手动重建）
PROMPT -- 2. 重建约束（原脚本 exclude=constraint，需手动重建）
PROMPT -- 3. 重建序列（若表用序列，需从源 schema 取 last_number 后创建）
PROMPT --- 参考：export_qditp_user.sql 的 exclude 参数已排除这些对象 ---

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 核对行数比对结果（src = dst）
PROMPT 2. 重建索引/约束/序列（或用 DBMS_METADATA.GET_DDL 从 dump 里提取）
PROMPT 3. 继续导入其他域（TXN / RIDE / PLAT）