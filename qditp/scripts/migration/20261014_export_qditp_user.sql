-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — QDITP_USER 域数据导出脚本（expdp）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md
-- 状态：v1.7 执行包（仅供评审，严禁直接在生产库执行）
-- 用途：用 Data Pump 导出 QDITP_USER 域 13 张表的实际数据
-- 执行前置：
--   1. 确认 Oracle Data Pump 权限（DBA 角色或 EXPTAB_FULL_DATABASE / IMPFULL_DATABASE）
--   2. 配置 EXP_DIR（Oracle 服务器路径，需写权限）
--   3. 确认源 schema QDITP 可访问（当前登录为 QDITP 或 DBA）
-- =============================================================================

SET VERIFY OFF
SET LINESIZE 200
SET PAGESIZE 100

-- =============================================================================
-- 配置区
-- =============================================================================
DEFINE EXP_DIR    = 'DATA_PUMP_DIR'          -- Oracle 服务器路径（用 CREATE DIRECTORY 创建）
DEFINE OUT_DIR    = '/data/dump/qditp_user'  -- 本地挂载点或 NFS 路径（给 DBA 下载用）
DEFINE DUMPFILE   = 'qditp_user.dmp'
DEFINE LOGFILE    = 'qditp_user_export.log'
DEFINE SCHEMA     = 'QDITP'

-- =============================================================================
-- expdp 命令行（在服务器 shell 里执行，不在 SQL*Plus 里）
-- =============================================================================
PROMPT ============================================================================
PROMPT QDITP_USER 域数据导出命令（在服务器 shell 执行）
PROMPT ============================================================================
PROMPT expdp <DBA_USER>/<DBA_PWD>@//172.20.222.3:1521/AFCITPDB \
PROMPT   directory=&EXP_DIR \
PROMPT   dumpfile=&DUMPFILE \
PROMPT   logfile=&LOGFILE \
PROMPT   tables=&SCHEMA..ACCOUNT_EXCEPTION_TICKET,&SCHEMA..ALIPAY_PHONE_CHANGE_LOG, \
PROMPT          &SCHEMA..APP_USER_PAY_CHANNEL,&SCHEMA..USER_ACC_EMPLOYEE_CARD, \
PROMPT          &SCHEMA..USER_ACC_EMPLOYEE_CARD_LOG,&SCHEMA..USER_ACC_TICKETNO, \
PROMPT          &SCHEMA..USER_ITP_REG_INFO,&SCHEMA..USER_ITP_REG_INFO_BAK20260909, \
PROMPT          &SCHEMA..USER_ITP_REG_LOG,&SCHEMA..USER_PHONE_CHANGE_LOG, \
PROMPT          &SCHEMA..LOGIC_CARD_POOL_BATCH,&SCHEMA..LOGIC_CARD_POOL_CARD, \
PROMPT          &SCHEMA..LOGIC_CARD_POOL_TYPE_LOCK \
PROMPT   parallel=4 \
PROMPT   compression=all \
PROMPT   exclude=index,constraint,trigger,grant,statistics \
PROMPT   job_name=exp_user_domain
PROMPT ============================================================================

-- =============================================================================
-- 导出前行数基线（记录到日志，导入后比对）
-- =============================================================================
PROMPT === 导出前行数基线 ===
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

-- =============================================================================
-- 导出后验证（检查 dump 文件大小 + 元数据完整性）
-- =============================================================================
PROMPT === 导出后验证（在服务器 shell 执行）===
PROMPT ls -lh &OUT_DIR/&DUMPFILE
PROMPT # 期望：dump 文件存在，大小合理（USER_ITP_REG_INFO 约 7 行，应该不会太大）
PROMPT # 检查 log 文件：
PROMPT tail -50 &OUT_DIR/&LOGFILE
PROMPT # 期望末尾有 "MASTER TABLE \"&SCHEMA\".\"EXP_USER_DOMAIN\" SUCCESSFULLY LOADED/UNLOADED"

PROMPT === 完成 ===
PROMPT 后续步骤：
PROMPT 1. 把 &OUT_DIR/&DUMPFILE 拷贝到目标机器
PROMPT 2. 在目标机器执行 import_qditp_user.sql
PROMPT 3. 导入后跑行数比对（见 import 脚本）