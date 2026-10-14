-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_TXN 域数据导出脚本（expdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（仅供评审，严禁直接在生产库执行）
-- 用途：用 Data Pump 导出 QDITP_TXN 域 ~36 张表的实际数据
-- 风险提示：
--   - F2F_ORDER/PAYMENT/RESULT_REPORT 带 GLOBAL 唯一索引，导出/导入时 exclude 掉，导入后重建
--   - 分区表导出时保留分区定义（expdp 默认行为）
--   - 容量热点表（GATE_TXN_PAY / PAY_CALLBACK_LOG / ALIPAY_PAY_CENTER_MSG_LOG / ALIPAY_PAY_CALLBACK_LOG）
--     数据量大，建议 parallel=8 + compression=all
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE EXP_DIR    = 'DATA_PUMP_DIR'
DEFINE OUT_DIR    = '/data/dump/qditp_txn'
DEFINE DUMPFILE   = 'qditp_txn.dmp'
DEFINE LOGFILE    = 'qditp_txn_export.log'
DEFINE SCHEMA     = 'QDITP'

PROMPT ============================================================================
PROMPT QDITP_TXN 域数据导出命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&EXP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   tables= \
PROMPT     &SCHEMA..APP_PAY_SIGN_INFO,&SCHEMA..APP_PAY_SIGN_LOG,&SCHEMA..APP_PAY_SIGN_REQUEST, \
PROMPT     &SCHEMA..APP_TERMINATION_REQUEST,&SCHEMA..PAY_TXN_DETAIL,&SCHEMA..PAY_REFUND_DETAIL,&SCHEMA..PAY_CALLBACK_LOG, \
PROMPT     &SCHEMA..ALIPAY_PAY_TXN_DETAIL,&SCHEMA..ALIPAY_REFUND_TXN_DETAIL, \
PROMPT     &SCHEMA..ALIPAY_PAY_CALLBACK_LOG,&SCHEMA..ALIPAY_PAY_CENTER_MSG_LOG, \
PROMPT     &SCHEMA..GATE_TXN_PAY,&SCHEMA..GATE_RETRY_QUEUE, \
PROMPT     &SCHEMA..SUPPLEMENT_ORDER,&SCHEMA..SUPPLEMENT_ORDER_ITEM, \
PROMPT     &SCHEMA..F2F_ORDER,&SCHEMA..F2F_PAYMENT,&SCHEMA..F2F_REFUND, \
PROMPT     &SCHEMA..F2F_RESULT_REPORT,&SCHEMA..F2F_TICKET, \
PROMPT     &SCHEMA..F2F_DEVICE_STATUS,&SCHEMA..F2F_NOTIFY_TASK, \
PROMPT     &SCHEMA..DAILY_TICKET_ORDER,&SCHEMA..DAILY_TICKET_INSTANCE, \
PROMPT     &SCHEMA..DAILY_TICKET_PAY_LOG,&SCHEMA..DAILY_TICKET_PAY_NOTIFY, \
PROMPT     &SCHEMA..DAILY_TICKET_REFUND,&SCHEMA..DAILY_TICKET_REFUND_DETAIL, \
PROMPT     &SCHEMA..DAILY_TICKET_USAGE_LOG,&SCHEMA..TRAVEL_TICKET_ORDER, \
PROMPT     &SCHEMA..RECON_BATCH,&SCHEMA..RECON_BATCH_FILE, \
PROMPT     &SCHEMA..RECON_BATCH_PART,&SCHEMA..RECON_BATCH_SOURCE, \
PROMPT     &SCHEMA..RECON_THIRD_BILL,&SCHEMA..RECON_TXN_DIFF \
PROMPT   parallel=8 \
PROMPT   compression=all \
PROMPT   exclude=index,constraint,trigger,grant,statistics \
PROMPT   job_name=exp_txn_domain
PROMPT ============================================================================

PROMPT === 导出前行数基线（关键表）===
BEGIN
  FOR t IN (
    SELECT 'GATE_TXN_PAY'          n FROM dual UNION ALL
    SELECT 'F2F_ORDER'             FROM dual UNION ALL
    SELECT 'F2F_PAYMENT'           FROM dual UNION ALL
    SELECT 'F2F_RESULT_REPORT'     FROM dual UNION ALL
    SELECT 'PAY_CALLBACK_LOG'      FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CALLBACK_LOG' FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CENTER_MSG_LOG' FROM dual UNION ALL
    SELECT 'RECON_BATCH'           FROM dual
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
END;
/

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 把 dump 文件拷贝到目标机器
PROMPT 2. 执行 import_qditp_txn.sql
PROMPT 3. 导入后跑行数比对