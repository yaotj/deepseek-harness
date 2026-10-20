-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_TXN 域数据导入脚本（impdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（⚠️ 同库设计；**跨库场景（红岛生产）以 20261017_m2m3_crossdb_hongdao.sql
--       为准**——本文件 impdp 连接串指向老环境、行数比对为同库假设，NEVER 直接用于红岛）
-- 用途：用 Data Pump 导入 qditp_txn.dmp 到 QDITP_TXN schema
-- 风险提示：
--   - F2F_ORDER/PAYMENT/RESULT_REPORT 的 GLOBAL 唯一索引需手动重建（exclude 掉）
--   - 分区表导入后分区定义保留（impdp 默认行为）
--   - 导入期 MUST 停 recon/face-pay/web-admin 定时任务（双跑硬约束）
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

DEFINE IMP_DIR    = 'DATA_PUMP_DIR'
DEFINE DUMPFILE   = 'qditp_txn.dmp'
DEFINE LOGFILE    = 'qditp_txn_import.log'
DEFINE SRC_SCHEMA = 'QDITP'
DEFINE TGT_SCHEMA = 'QDITP_TXN'

PROMPT ============================================================================
PROMPT QDITP_TXN 域数据导入命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT impdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&IMP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   remap_schema=&SRC_SCHEMA.:&TGT_SCHEMA. \
PROMPT   remap_tablespace=USERS:ITP_TXN_TS \
PROMPT   table_exists_action=replace \
PROMPT   parallel=8 \
PROMPT   job_name=imp_txn_domain
PROMPT ============================================================================

PROMPT === 导入后行数比对（关键表）===
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
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM ' || '&SRC_SCHEMA..' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM ' || '&TGT_SCHEMA..' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT === 导入后重建 GLOBAL 唯一索引（F2F_*）===
PROMPT -- ⚠️  以下索引原为 GLOBAL，导入后需重建（或在导出时不排除）
PROMPT -- 方案 A：导出时不排除索引，impdp 自动重建（推荐）
PROMPT -- 方案 B：导出时 exclude=index，导入后手动重建（本脚本采用）
PROMPT
PROMPT -- 重建 F2F_ORDER 的全局唯一索引（示例，实际 index_name 需从源 schema 查）
-- ALTER TABLE QDITP_TXN.F2F_ORDER ADD CONSTRAINT <原唯一约束名> UNIQUE (<列>) USING INDEX TABLESPACE ITP_TXN_TS;
PROMPT -- 注意：若改 LOCAL，必须把分区键纳入唯一约束（见 M0 §1.3）

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 核对行数比对结果
PROMPT 2. 重建索引/约束/序列
PROMPT 3. 启动被暂停的定时任务（recon/face-pay/web-admin）
PROMPT 4. 继续导入其他域（USER / RIDE / PLAT）