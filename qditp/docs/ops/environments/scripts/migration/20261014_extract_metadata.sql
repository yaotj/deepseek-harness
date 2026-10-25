-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 元数据抽取脚本（通用工具）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §4
-- 状态：草稿 v1.4（仅供评审，严禁直接在生产库执行）
-- 用途：用 DBMS_METADATA.GET_DDL 从 QDITP schema 抽取 130 张表的完整 DDL
--       （含分区定义 / 索引 / 约束 / 序列 / 触发器），输出到 spool 文件
-- 执行前置：
--   1. 确认当前登录为 QDITP schema（原业务库）
--   2. DBA 已授予 EXECUTE ON DBMS_METADATA
--   3. 设置 OUTFILE 路径（需写权限）
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 32767
SET PAGESIZE 0
SET LONG 1000000
SET LONGCHUNKSIZE 32767
SET TRIMSPOOL ON
SET RECSEP OFF
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- 配置区：替换 OUTFILE 路径（SQL*Plus 服务器路径，非客户端）
--         若用 OEM / SQL Developer，可直接复制输出而不需 SPOOL
-- =============================================================================
DEFINE OUTFILE_BASE = '/tmp/qditp_split_ddl'

-- =============================================================================
-- 表清单：按目标 schema 分组（与 docs/ops/数据库按域分库计划.md §3 对齐）
-- =============================================================================

-- QDITP_USER（13 张）
DEFINE TBL_USER = 'ACCOUNT_EXCEPTION_TICKET,ALIPAY_PHONE_CHANGE_LOG,APP_USER_PAY_CHANNEL,
                   USER_ACC_EMPLOYEE_CARD,USER_ACC_EMPLOYEE_CARD_LOG,USER_ACC_TICKETNO,
                   USER_ITP_REG_INFO,USER_ITP_REG_INFO_BAK20260909,USER_ITP_REG_LOG,
                   USER_PHONE_CHANGE_LOG,LOGIC_CARD_POOL_BATCH,LOGIC_CARD_POOL_CARD,
                   LOGIC_CARD_POOL_TYPE_LOCK'

-- QDITP_TXN（~36 张）
DEFINE TBL_TXN = 'APP_PAY_SIGN_INFO,APP_PAY_SIGN_LOG,APP_PAY_SIGN_REQUEST,
                 APP_TERMINATION_REQUEST,PAY_TXN_DETAIL,PAY_REFUND_DETAIL,PAY_CALLBACK_LOG,
                 ALIPAY_PAY_TXN_DETAIL,ALIPAY_REFUND_TXN_DETAIL,ALIPAY_PAY_CALLBACK_LOG,
                 ALIPAY_PAY_CENTER_MSG_LOG,
                 GATE_TXN_PAY,GATE_RETRY_QUEUE,SUPPLEMENT_ORDER,SUPPLEMENT_ORDER_ITEM,
                 F2F_ORDER,F2F_PAYMENT,F2F_REFUND,F2F_RESULT_REPORT,
                 F2F_TICKET,F2F_DEVICE_STATUS,F2F_NOTIFY_TASK,
                 DAILY_TICKET_ORDER,DAILY_TICKET_INSTANCE,DAILY_TICKET_PAY_LOG,
                 DAILY_TICKET_PAY_NOTIFY,DAILY_TICKET_REFUND,DAILY_TICKET_REFUND_DETAIL,
                 DAILY_TICKET_USAGE_LOG,TRAVEL_TICKET_ORDER,
                 RECON_BATCH,RECON_BATCH_FILE,RECON_BATCH_PART,
                 RECON_BATCH_SOURCE,RECON_THIRD_BILL,RECON_TXN_DIFF'

-- QDITP_RIDE（4 张）
DEFINE TBL_RIDE = 'QRCODE_STATUS,QRCODE_TXN_DETAIL,QRCODE_SUPPLEMENT_REQUEST,STATION_INFO'

-- QDITP_PLAT（~77 张）
DEFINE TBL_PLAT = 'BLACKLIST,BLACKLIST_OPERATE_LOG,BLACKLIST_RELEASED,
                   METRO_CA_KEYSTORE,METRO_MEMBER_STATIC_KEY,
                   TBL_ADD_PARA,TBL_BASE_FARE,TBL_CHIP_TYPE,TBL_FARE_GROUP,TBL_FARE_MATRIX,
                   TBL_FARE_TIME,TBL_LINE_INFO,TBL_ORDER_REFUND_CYCLE,TBL_PARA_VERSION,
                   TBL_RISK_CONTROL_LOG,TBL_RISK_GROUP,TBL_RISK_RULE,TBL_SECT_INFO,
                   TBL_SINGLE_TICKET_PURCHASE_LIMIT,TBL_SPECIAL_DATE,TBL_STATION_INFO,
                   TBL_TICKET_FARE,TBL_TICKET_TYPE,TBL_TIME_INTERVAL,TBL_TOTAL_SALE_PART,
                   TBL_TSF_INFO,TBL_ZONE_DTL,TBL_ZONE_INFO,TBL_TKT_PRE_PERSON,
                   TBL_STL_ACCT_INFO,TBL_STL_PERSON_INFO,TBL_STL_TICKET_INFO,TBL_STL_TICKET_SET,
                   TBL_TKT_ES_ACCOUNT,TBL_TKT_ES_ASSIGN,TBL_TKT_ES_FILE_PROC_LOG,
                   TBL_TKT_ES_INFO,TBL_TKT_ES_PROC,TBL_TKT_ES_REPORT,TBL_TKT_ES_TASK,
                   TBL_TKT_TASK_PLAN,ACC_SIMULATION_HISTORY,ACC_SIM_EMPLOYEE_CARD,
                   SYS_USER,SYS_ROLE,SYS_MENU,SYS_DEPT,SYS_POST,SYS_DICT_TYPE,SYS_DICT_DATA,
                   SYS_CONFIG,SYS_NOTICE,SYS_LOGININFOR,SYS_OPER_LOG,SYS_JOB,
                   QRTZ_JOB_DETAILS,QRTZ_TRIGGERS,QRTZ_SIMPLE_TRIGGERS,QRTZ_CRON_TRIGGERS,
                   QRTZ_BLOB_TRIGGERS,QRTZ_CALENDARS,QRTZ_PAUSED_TRIGGER_GRPS,
                   QRTZ_FIRED_TRIGGERS,QRTZ_SCHEDULER_STATE,QRTZ_LOCKS,
                   GEN_TABLE,GEN_TABLE_COLUMN,
                   COM_DEVICE_SYN_KEY,METRO_AGM_KEY_POOL,METRO_AGM_KEY_VERSION,
                   TBL_NOTICE_APP_FAILURE_RECORD'

-- =============================================================================
-- 函数：抽取单表完整 DDL（含分区 / 索引 / 约束）
--       输出到 spool 文件，文件名 {OUTFILE_BASE}/{TARGET_SCHEMA}_{TABLE}.sql
-- =============================================================================
SET SERVEROUTPUT ON

DECLARE
  v_src_schema VARCHAR2(30) := 'QDITP';
  v_out_base   VARCHAR2(200) := '&OUTFILE_BASE';
  v_table_name VARCHAR2(128);
  v_target_schema VARCHAR2(30);
  v_hdl NUMBER;
  v_ddl CLOB;
  v_file UTL_FILE.FILE_TYPE;
  v_buf VARCHAR2(32767);
  v_off NUMBER;
  v_chunk NUMBER := 32000;
BEGIN
  -- 这里用动态 SQL 按清单循环；实际执行时建议改用脚本逐表调用下面过程
  NULL;  -- 见下方 @begin_extraction 标记
END;
/

-- =============================================================================
-- 辅助：逐表导出（在 SQL*Plus 里对每张表执行下面的块，或包装成循环脚本）
-- =============================================================================
-- 用法示例（以 GATE_TXN_PAY 迁往 QDITP_TXN 为例）：
--   @extract_single_table.sql QDITP GATE_TXN_PAY QDITP_TXN

-- extract_single_table.sql 内容：
-- DEFINE SRC_SCHEMA = '&1'
-- DEFINE SRC_TABLE  = '&2'
-- DEFINE TGT_SCHEMA = '&3'
-- DEFINE OUTFILE    = '&4'   -- 可选，默认 <OUTFILE_BASE>_<TGT_SCHEMA>_<TABLE>.sql

-- SET LONG 1000000 LONGCHUNKSIZE 32767 PAGESIZE 0 TRIMSPOOL ON RECSEP OFF
-- SPOOL &OUTFILE
-- SELECT DBMS_METADATA.GET_DDL('TABLE', '&SRC_TABLE', '&SRC_SCHEMA') FROM dual;
-- SELECT DBMS_METADATA.GET_DEPENDENT_DDL('INDEX', '&SRC_TABLE', '&SRC_SCHEMA') FROM dual;
-- SELECT DBMS_METADATA.GET_DEPENDENT_DDL('CONSTRAINT', '&SRC_TABLE', '&SRC_SCHEMA') FROM dual;
-- SELECT DBMS_METADATA.GET_DEPENDENT_DDL('TRIGGER', '&SRC_TABLE', '&SRC_SCHEMA') FROM dual;
-- SELECT DBMS_METADATA.GET_DDL('SEQUENCE', seq.sequence_name, '&SRC_SCHEMA')
--   FROM user_sequences seq
--   WHERE seq.sequence_name IN (
--     SELECT 'SEQ_'||t.table_name FROM user_tables t WHERE t.table_name='&SRC_TABLE'
--     UNION ALL
--     SELECT t.sequence_name FROM user_tables t WHERE t.table_name='&SRC_TABLE' AND t.identity_column IS NOT NULL
--   );
-- SPOOL OFF
-- -- 替换 schema 前缀（用 sed / python 批量）：
-- -- sed -i 's/QDITP\./QDITP_TXN./g' <OUTFILE>
-- -- 注意：TABLESPACE 子句里的旧 schema 前缀也要一起替换或手工清理

PROMPT === 元数据抽取说明 ===
PROMPT 1. 本脚本提供工具框架；实际执行请用下方 "批量抽取循环" 段
PROMPT 2. 或用 Oracle SQLcl / DBMS_METADATA 的 getTransformDDl 自动替换 schema
PROMPT 3. 分区表（F2F_* / GATE_TXN_PAY / QRCODE_TXN_DETAIL 等）的 DDL 会携带 PARTITION 子句，保留
PROMPT 4. GLOBAL 索引（F2F_ORDER/PAYMENT/RESULT_REPORT）迁移后如需归档，DDL 须带 UPDATE GLOBAL INDEXES
PROMPT 5. 执行完后做行数比对：SELECT COUNT(*) FROM QDITP.<t> vs SELECT COUNT(*) FROM QDITP_TXN.<t>