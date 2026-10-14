-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 包 4：M4 校验 + 回滚
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §9 / §10
-- 状态：v1.6 执行包（仅供评审，严禁直接在生产库执行）
-- 目标：全量行数比对 + 索引/分区/同义词状态校验 + 回滚预案
-- 执行时机：M2/M3 迁移完成后、正式切换 datasource 前
-- 回滚：本包包含回滚片段（4 级递进），出异常时按顺序执行
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============================================================================
PROMPT 包 4：M4 校验 + 回滚预案
PROMPT ============================================================================
PROMPT 执行前 MUST：
PROMPT 1. 包 1–3 已全部完成（分区补齐 + 表空间 + schema + DDL 导出 + 逐表灌数）
PROMPT 2. 当前登录为 QDITP schema（原业务库）
PROMPT 3. 准备 4 个目标 schema 的连接串（QDITP_USER / QDITP_TXN / QDITP_RIDE / QDITP_PLAT）
PROMPT ============================================================================

-- =============================================================================
-- Step 1：全量行数比对（抽样关键表）
-- =============================================================================
PROMPT === 1.1 QDITP_USER 域行数比对 ===
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
      || '(SELECT COUNT(*) FROM QDITP.' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM QDITP_USER.' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT === 1.2 QDITP_TXN 域行数比对（关键表）===
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
      || '(SELECT COUNT(*) FROM QDITP.' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM QDITP_TXN.' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

PROMPT === 1.3 QDITP_RIDE 域行数比对 ===
BEGIN
  FOR t IN (
    SELECT 'QRCODE_STATUS'          n FROM dual UNION ALL
    SELECT 'QRCODE_TXN_DETAIL'      FROM dual UNION ALL
    SELECT 'QRCODE_SUPPLEMENT_REQUEST' FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM QDITP.' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM QDITP_RIDE.' || t.n || ') dst FROM dual;'
    );
  END LOOP;
  DBMS_OUTPUT.PUT_LINE('-- STATION_INFO 为同义词，指向 QDITP_PLAT.TBL_STATION_INFO，无需比对');
END;
/

PROMPT === 1.4 QDITP_PLAT 域行数比对（抽样关键表）===
BEGIN
  FOR t IN (
    SELECT 'BLACKLIST'            n FROM dual UNION ALL
    SELECT 'TBL_STATION_INFO'     FROM dual UNION ALL
    SELECT 'TBL_LINE_INFO'        FROM dual UNION ALL
    SELECT 'SYS_USER'             FROM dual UNION ALL
    SELECT 'SYS_JOB'              FROM dual UNION ALL
    SELECT 'QRTZ_JOB_DETAILS'     FROM dual
  ) LOOP
    DBMS_OUTPUT.PUT_LINE(
      'SELECT ''' || t.n || ''' tbl, '
      || '(SELECT COUNT(*) FROM QDITP.' || t.n || ') src, '
      || '(SELECT COUNT(*) FROM QDITP_PLAT.' || t.n || ') dst FROM dual;'
    );
  END LOOP;
END;
/

-- =============================================================================
-- Step 2：索引 / 分区 / 同义词状态校验
-- =============================================================================
PROMPT === 2.1 F2F_* GLOBAL 索引状态（应为 VALID）===
SELECT i.table_name, i.index_name, i.status, i.partitioned
FROM   user_indexes i
WHERE  i.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
ORDER BY i.table_name, i.index_name
;

PROMPT === 2.2 分区表 2027 分区是否补齐（期望每张表 12 行）===
SELECT table_name, COUNT(*) AS partition_count
FROM   user_tab_partitions
WHERE  table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
)
  AND partition_name LIKE 'P2027%'
GROUP BY table_name
ORDER BY table_name
;

PROMPT === 2.3 跨库同义词是否解析正常 ===
SELECT owner, synonym_name, table_owner, table_name, status
FROM   dba_synonyms
WHERE  owner IN ('QDITP_USER','QDITP_TXN','QDITP_RIDE','itp_ro')
  AND   table_owner IN ('QDITP_PLAT','QDITP_USER')
ORDER BY owner, synonym_name
;

-- =============================================================================
-- Step 3：回滚预案（4 级递进，出异常时按顺序执行）
-- =============================================================================
PROMPT === 3.1 级：撤销跨库同义词 + GRANT（可逆，不影响数据）===
-- 在对应 schema 下执行（PLAT / USER / TXN / RIDE）
-- REVOKE SELECT ON QDITP_PLAT.BLACKLIST FROM QDITP_USER, QDITP_TXN, QDITP_RIDE;
-- DROP SYNONYM QDITP_USER.BLACKLIST;
-- ... 详见 rollback_split.sql

PROMPT === 3.2 级：收回 QDITP_MIGRATOR 角色 ===
-- REVOKE QDITP_MIGRATOR FROM <迁移执行账号>;

PROMPT === 3.3 级：切回原 datasource（秒级回滚）===
-- 把各模块 application.properties 的 datasource 指回 QDITP
-- 重启应用即可恢复原状
-- 源表仍保留为 BAK_* / _PRE_SPLIT，数据未丢失

PROMPT === 3.4 级：兜底（删除新 schema + 表空间，慎用）===
-- 仅在 M4 校验失败且决定放弃本次分库时执行
-- DROP USER QDITP_USER CASCADE;
-- DROP USER QDITP_TXN   CASCADE;
-- DROP USER QDITP_RIDE  CASCADE;
-- DROP USER QDITP_PLAT  CASCADE;
-- DROP USER itp_ro CASCADE;
-- DROP TABLESPACE ITP_TXN_TS       INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TS      INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_TXN_TEMP     INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_PLAT_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_USER_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP TABLESPACE ITP_RIDE_TEMP    INCLUDING CONTENTS AND DATAFILES;
-- DROP ROLE QDITP_MIGRATOR;

-- =============================================================================
-- Step 4：正式切换 datasource（校验通过后执行）
-- =============================================================================
PROMPT === 4.1 各模块 datasource 切换清单 ===
PROMPT - account-server / alipay-account-server / card-pool-server → QDITP_USER
PROMPT - pay-sign-server / alipay-pay-sign-server / gate-txn-pay-server
PROMPT       / face-pay-server / daily-ticket-server / recon-server → QDITP_TXN
PROMPT - ticket-server → QDITP_RIDE
PROMPT - blacklist-server / key-server / para-server / acc-es-server
PROMPT       / employee-card-acc-simulator / web-server → QDITP_PLAT
PROMPT - qdscg 网关 → itp_ro（已配置，无需改）

PROMPT === 4.2 切换后验证 ===
PROMPT 1. curl 各模块健康检查端点（/actuator/health 或自定义）
PROMPT 2. 跑一笔真实交易（开户 / 支付签约 / 过闸），确认无 ORA- 错误
PROMPT 3. 观察日志 30 分钟，确认无跨库 JOIN 报错（应无，交易数据不跨库）

PROMPT === 完成 ===
PROMPT 包 4 完成，分库迁移工作闭环
PROMPT 后续：cooldown ≥7 天后，执行 BAK_* / _PRE_SPLIT 表的 DROP（见 rollback_split.sql 第 4 级注释）
COMMIT;