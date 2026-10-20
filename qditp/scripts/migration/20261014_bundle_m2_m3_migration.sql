-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 包 3：M2/M3 迁移（4 库 DDL 导出 + 灌数 + 校验）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §4 / §6 / §7
-- 状态：v1.6 执行包（⚠️ 同库设计，仅供评审；**跨库场景（老环境→红岛生产）已作废**）
-- ⚠️ 2026-10-17：本包 Step 2 的 `INSERT INTO QDITP_TXN.x SELECT * FROM QDITP.x` 只在
--    **同库**成立；实际迁移是跨库（源 172.20.222.3/AFCITPDB → 目标 172.120.110.25/hditpdb），
--    MUST 改用 20261017_m2m3_crossdb_hongdao.sql（expdp → 传输 → impdp REMAP_SCHEMA）。
--    Step 1 的 DDL 导出（GET_DDL）作为评审留档仍有参考价值，数据灌数 NEVER 用本包。
-- 目标：把 130 张表按域迁入 4 个新 schema，保留分区 / 索引 / 约束
-- 执行顺序：
--   1. export_qditp_plat.sql   ← PLAT 域 ~77 张表 DDL 导出
--   2. export_qditp_user.sql   ← USER 域 13 张表 DDL 导出
--   3. export_qditp_ride.sql   ← RIDE 域 3 张表 + STATION_INFO 同义词
--   4. export_qditp_txn.sql    ← TXN 域 ~36 张表 DDL 导出
--   5. 逐表灌数（CTAS 快照 → GET_DDL → APPEND → RENAME）
--   6. 行数比对（M4 校验）
-- 前置依赖：包 2（M1 基础建设）已完成（4 schema + 4 TS 已创建）
-- 双跑风险：TXN 域迁移期 MUST 停 recon / face-pay / web-admin 定时任务或加 @SchedulerLock
-- 回滚：rollback_split.sql（包内已包含回滚片段）
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 32767
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============================================================================
PROMPT 包 3：M2/M3 迁移 — 4 库 DDL 导出 + 灌数 + 校验
PROMPT ============================================================================
PROMPT 执行前 MUST：
PROMPT 1. 包 2 已完成（4 schema + 4 TS 已创建）
PROMPT 2. 当前登录为 QDITP schema（原业务库）
PROMPT 3. 配置 TMP_DIR（Oracle 服务器路径，需写权限）
PROMPT 4. TXN 域迁移期提前通知业务方停相关定时任务
PROMPT ============================================================================

-- =============================================================================
-- Step 1：导出 4 个域的 DDL（每表一个 .sql 文件）
-- 以下 PL/SQL 块与原 export_*.sql 逻辑一致，直接嵌入本包
-- =============================================================================

-- 1.1 QDITP_PLAT 域（~77 张表）
PROMPT [1/4] 导出 QDITP_PLAT 域 DDL（~77 张表）...
DECLARE
  v_src  VARCHAR2(30) := 'QDITP';
  v_tgt  VARCHAR2(30) := 'QDITP_PLAT';
  v_base VARCHAR2(200) := '/tmp/qditp_split_ddl/qditp_plat';
  v_ddl  CLOB;
  v_f    UTL_FILE.FILE_TYPE;
BEGIN
  FOR v_tbl IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN (
      'BLACKLIST','BLACKLIST_OPERATE_LOG','BLACKLIST_RELEASED',
      'METRO_CA_KEYSTORE','METRO_MEMBER_STATIC_KEY',
      'TBL_ADD_PARA','TBL_BASE_FARE','TBL_CHIP_TYPE','TBL_FARE_GROUP','TBL_FARE_MATRIX',
      'TBL_FARE_TIME','TBL_LINE_INFO','TBL_ORDER_REFUND_CYCLE','TBL_PARA_VERSION',
      'TBL_RISK_CONTROL_LOG','TBL_RISK_GROUP','TBL_RISK_RULE','TBL_SECT_INFO',
      'TBL_SINGLE_TICKET_PURCHASE_LIMIT','TBL_SPECIAL_DATE','TBL_STATION_INFO',
      'TBL_TICKET_FARE','TBL_TICKET_TYPE','TBL_TIME_INTERVAL','TBL_TOTAL_SALE_PART',
      'TBL_TSF_INFO','TBL_ZONE_DTL','TBL_ZONE_INFO','TBL_TKT_PRE_PERSON',
      'TBL_STL_ACCT_INFO','TBL_STL_PERSON_INFO','TBL_STL_TICKET_INFO','TBL_STL_TICKET_SET',
      'TBL_TKT_ES_ACCOUNT','TBL_TKT_ES_ASSIGN','TBL_TKT_ES_FILE_PROC_LOG',
      'TBL_TKT_ES_INFO','TBL_TKT_ES_PROC','TBL_TKT_ES_REPORT','TBL_TKT_ES_TASK',
      'TBL_TKT_TASK_PLAN','ACC_SIMULATION_HISTORY','ACC_SIM_EMPLOYEE_CARD',
      'SYS_USER','SYS_ROLE','SYS_MENU','SYS_DEPT','SYS_POST',
      'SYS_DICT_TYPE','SYS_DICT_DATA','SYS_CONFIG','SYS_NOTICE',
      'SYS_LOGININFOR','SYS_OPER_LOG','SYS_JOB',
      'SYS_USER_ROLE','SYS_ROLE_MENU','SYS_ROLE_DEPT','SYS_USER_POST','SYS_JOB_LOG',
      'QRTZ_JOB_DETAILS','QRTZ_TRIGGERS','QRTZ_SIMPLE_TRIGGERS','QRTZ_CRON_TRIGGERS',
      'QRTZ_BLOB_TRIGGERS','QRTZ_CALENDARS','QRTZ_PAUSED_TRIGGER_GRPS',
      'QRTZ_FIRED_TRIGGERS','QRTZ_SCHEDULER_STATE','QRTZ_LOCKS',
      'GEN_TABLE','GEN_TABLE_COLUMN',
      'COM_DEVICE_SYN_KEY','METRO_AGM_KEY_POOL','METRO_AGM_KEY_VERSION',
      'TBL_NOTICE_APP_FAILURE_RECORD'
    )
    ORDER BY table_name
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', v_tgt || '_' || v_tbl.table_name || '.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || v_tbl.table_name || ' -> ' || v_tgt);
    v_ddl := DBMS_METADATA.GET_DDL('TABLE', v_tbl.table_name, v_src);
    v_ddl := REGEXP_REPLACE(v_ddl, '("?' || v_src || '"?\.)', '"' || v_tgt || '".', 1, 0, 'i');
    v_ddl := REGEXP_REPLACE(v_ddl, 'TABLESPACE\s+"?' || v_src || '"?\.', 'TABLESPACE ITP_PLAT_TS.', 1, 0, 'i');
    UTL_FILE.PUT(v_f, v_ddl);
    UTL_FILE.PUT_LINE(v_f, ';');
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('  [' || v_tgt || '] ' || v_tbl.table_name);
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('  -> ' || v_base || '/');
END;
/

-- 1.2 QDITP_USER 域（13 张表）
PROMPT [2/4] 导出 QDITP_USER 域 DDL（13 张表）...
DECLARE
  v_src  VARCHAR2(30) := 'QDITP';
  v_tgt  VARCHAR2(30) := 'QDITP_USER';
  v_ddl  CLOB;
  v_f    UTL_FILE.FILE_TYPE;
BEGIN
  FOR v_tbl IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN (
      'ACCOUNT_EXCEPTION_TICKET','ALIPAY_PHONE_CHANGE_LOG','APP_USER_PAY_CHANNEL',
      'USER_ACC_EMPLOYEE_CARD','USER_ACC_EMPLOYEE_CARD_LOG','USER_ACC_TICKETNO',
      'USER_ITP_REG_INFO','USER_ITP_REG_INFO_BAK20260909','USER_ITP_REG_LOG',
      'USER_PHONE_CHANGE_LOG','LOGIC_CARD_POOL_BATCH','LOGIC_CARD_POOL_CARD',
      'LOGIC_CARD_POOL_TYPE_LOCK'
    )
    ORDER BY table_name
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', v_tgt || '_' || v_tbl.table_name || '.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || v_tbl.table_name || ' -> ' || v_tgt);
    v_ddl := DBMS_METADATA.GET_DDL('TABLE', v_tbl.table_name, v_src);
    v_ddl := REGEXP_REPLACE(v_ddl, '("?' || v_src || '"?\.)', '"' || v_tgt || '".', 1, 0, 'i');
    v_ddl := REGEXP_REPLACE(v_ddl, 'TABLESPACE\s+"?' || v_src || '"?\.', 'TABLESPACE ITP_USER_TS.', 1, 0, 'i');
    UTL_FILE.PUT(v_f, v_ddl);
    UTL_FILE.PUT_LINE(v_f, ';');
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('  [' || v_tgt || '] ' || v_tbl.table_name);
  END LOOP;
END;
/

-- 1.3 QDITP_RIDE 域（3 张实体表 + STATION_INFO 同义词）
PROMPT [3/4] 导出 QDITP_RIDE 域 DDL（3 张表 + 同义词）...
DECLARE
  v_src  VARCHAR2(30) := 'QDITP';
  v_tgt  VARCHAR2(30) := 'QDITP_RIDE';
  v_ddl  CLOB;
  v_f    UTL_FILE.FILE_TYPE;
BEGIN
  FOR v_tbl IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN ('QRCODE_STATUS','QRCODE_TXN_DETAIL','QRCODE_SUPPLEMENT_REQUEST')
    ORDER BY table_name
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', v_tgt || '_' || v_tbl.table_name || '.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || v_tbl.table_name || ' -> ' || v_tgt);
    IF v_tbl.table_name = 'QRCODE_TXN_DETAIL' THEN
      UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 容量热点，优先排归档');
    END IF;
    v_ddl := DBMS_METADATA.GET_DDL('TABLE', v_tbl.table_name, v_src);
    v_ddl := REGEXP_REPLACE(v_ddl, '("?' || v_src || '"?\.)', '"' || v_tgt || '".', 1, 0, 'i');
    v_ddl := REGEXP_REPLACE(v_ddl, 'TABLESPACE\s+"?' || v_src || '"?\.', 'TABLESPACE ITP_RIDE_TS.', 1, 0, 'i');
    UTL_FILE.PUT(v_f, v_ddl);
    UTL_FILE.PUT_LINE(v_f, ';');
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('  [' || v_tgt || '] ' || v_tbl.table_name);
  END LOOP;
  -- STATION_INFO 同义词（不迁实体表）
  v_f := UTL_FILE.FOPEN('TMP_DIR', v_tgt || '_SYNONYM_STATION_INFO.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- STATION_INFO 同义词 -> QDITP_PLAT.TBL_STATION_INFO');
  UTL_FILE.PUT_LINE(v_f, 'GRANT SELECT ON QDITP_PLAT.TBL_STATION_INFO TO QDITP_RIDE;');
  UTL_FILE.PUT_LINE(v_f, 'CREATE SYNONYM QDITP_RIDE.STATION_INFO FOR QDITP_PLAT.TBL_STATION_INFO;');
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('  [' || v_tgt || '] STATION_INFO (synonym)');
END;
/

-- 1.4 QDITP_TXN 域（~36 张表，含 F2F_* GLOBAL 索引警告）
PROMPT [4/4] 导出 QDITP_TXN 域 DDL（~36 张表）...
DECLARE
  v_src  VARCHAR2(30) := 'QDITP';
  v_tgt  VARCHAR2(30) := 'QDITP_TXN';
  v_ddl  CLOB;
  v_f    UTL_FILE.FILE_TYPE;
BEGIN
  FOR v_tbl IN (
    SELECT table_name FROM user_tables
    WHERE table_name IN (
      'APP_PAY_SIGN_INFO','APP_PAY_SIGN_LOG','APP_PAY_SIGN_REQUEST',
      'APP_TERMINATION_REQUEST','PAY_TXN_DETAIL','PAY_REFUND_DETAIL','PAY_CALLBACK_LOG',
      'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL','ALIPAY_PAY_CALLBACK_LOG',
      'ALIPAY_PAY_CENTER_MSG_LOG',
      'GATE_TXN_PAY','GATE_RETRY_QUEUE','SUPPLEMENT_ORDER','SUPPLEMENT_ORDER_ITEM',
      'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
      'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
      'DAILY_TICKET_ORDER','DAILY_TICKET_INSTANCE','DAILY_TICKET_PAY_LOG',
      'DAILY_TICKET_PAY_NOTIFY','DAILY_TICKET_REFUND','DAILY_TICKET_REFUND_DETAIL',
      'DAILY_TICKET_USAGE_LOG','TRAVEL_TICKET_ORDER',
      'RECON_BATCH','RECON_BATCH_FILE','RECON_BATCH_PART',
      'RECON_BATCH_SOURCE','RECON_THIRD_BILL','RECON_TXN_DIFF'
    )
    ORDER BY table_name
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', v_tgt || '_' || v_tbl.table_name || '.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || v_tbl.table_name || ' -> ' || v_tgt);
    IF v_tbl.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT') THEN
      UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 此表带 GLOBAL 唯一索引，归档 DDL 必须带 UPDATE GLOBAL INDEXES');
    END IF;
    IF v_tbl.table_name IN ('GATE_TXN_PAY','QRCODE_TXN_DETAIL',
                            'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
                            'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG') THEN
      UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 此表为 RANGE 分区表，检查分区上界是否已到 P202612');
    END IF;
    v_ddl := DBMS_METADATA.GET_DDL('TABLE', v_tbl.table_name, v_src);
    v_ddl := REGEXP_REPLACE(v_ddl, '("?' || v_src || '"?\.)', '"' || v_tgt || '".', 1, 0, 'i');
    v_ddl := REGEXP_REPLACE(v_ddl, 'TABLESPACE\s+"?' || v_src || '"?\.', 'TABLESPACE ITP_TXN_TS.', 1, 0, 'i');
    UTL_FILE.PUT(v_f, v_ddl);
    UTL_FILE.PUT_LINE(v_f, ';');
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('  [' || v_tgt || '] ' || v_tbl.table_name);
  END LOOP;
END;
/

-- =============================================================================
-- Step 2：逐表灌数模板（CTAS 快照 → GET_DDL 重建 → APPEND 灌数 → RENAME 回滚锚点）
-- 以下以 GATE_TXN_PAY 为例，其余表照此套用
-- =============================================================================
PROMPT ============================================================================
PROMPT 包 3 继续 — 逐表灌数（以 GATE_TXN_PAY 为例）
PROMPT ============================================================================

-- 2.1 CTAS 快照兜底（QDITP 下）
CREATE TABLE QDITP.BAK_GATE_TXN_PAY_20261014 AS SELECT * FROM QDITP.GATE_TXN_PAY;
PROMPT [灌数] BAK_GATE_TXN_PAY_20261014 快照完成

-- 2.2 取目标 DDL 并执行（从 Step 1 导出的文件中取，或手动跑 GET_DDL）
-- 参考：scripts/migration/20261014_export_qditp_txn.sql 生成的 QDITP_TXN_GATE_TXN_PAY.sql
-- 此处仅展示流程，实际执行需把导出的 .sql 文件在 QDITP_TXN 下跑

-- 2.3 APPEND 灌数
INSERT /*+ APPEND */ INTO QDITP_TXN.GATE_TXN_PAY SELECT * FROM QDITP.GATE_TXN_PAY;
COMMIT;
PROMPT [灌数] GATE_TXN_PAY 灌数完成

-- 2.4 源表 RENAME 回滚锚点（不 DROP）
ALTER TABLE QDITP.GATE_TXN_PAY RENAME TO QDITP.GATE_TXN_PAY_PRE_SPLIT;
PROMPT [灌数] GATE_TXN_PAY 源表已重命名为回滚锚点

-- 2.5 收集统计
EXEC DBMS_STATS.GATHER_TABLE_STATS('QDITP_TXN', 'GATE_TXN_PAY');
PROMPT [灌数] 统计信息已收集

-- =============================================================================
-- Step 3：行数比对（M4 校验模板）
-- =============================================================================
PROMPT ============================================================================
PROMPT 包 3 继续 — 行数比对（迁完所有表后跑）
PROMPT ============================================================================
BEGIN
  FOR t IN (
    SELECT 'GATE_TXN_PAY' n FROM dual UNION ALL
    SELECT 'F2F_ORDER'    FROM dual UNION ALL
    SELECT 'PAY_CALLBACK_LOG' FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM QDITP.' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM QDITP_TXN.' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT ============================================================================
PROMPT 包 3 完成，下一步操作：
PROMPT ============================================================================
PROMPT 1. 把 /tmp/qditp_split_ddl/qditp_{plat,user,ride,txn}/*.sql 拷贝到目标机器
PROMPT 2. 逐文件在目标 schema 下执行（sqlplus QDITP_*/<pwd>@//172.20.222.3:1521/AFCITPDB @*.sql）
PROMPT 3. 每张表灌数后跑行数比对，确认一致再继续下一张
PROMPT 4. TXN 域迁移期 MUST 停 recon / face-pay / web-admin 定时任务（双跑硬约束）
PROMPT 5. 全部迁完后跑完整行数比对（见包 4）
PROMPT ============================================================================