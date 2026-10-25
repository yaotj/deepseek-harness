-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 包 1：M0 P0 修复（分区补建 + 容量基线）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §1
-- 状态：v1.6 执行包（仅供评审，严禁直接在生产库执行）
-- 目标：在分库迁移前先把 15 张分区表的 2027 分区补齐，避免 2027-01-01 失效
-- 执行顺序：
--   1. m0_precheck.sql          ← 分区表盘点（7 段查询）
--   2. m0_generate_add_partition.sql  ← 生成 15 个分区 DDL（每表一个文件）
--   3. 人工复核生成的 .sql 文件 → 给 DBA 审批 → 维护窗口执行
-- 前置依赖：无（直接在原 QDITP schema 下执行）
-- 回滚：分区 DDL 本身不可逆（ADD PARTITION 只能 DROP），但可通过 DROP PARTITION 回退
--       （若已写入数据则不能 DROP，需用 TRUNCATE PARTITION 代替）
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT ============================================================================
PROMPT 包 1：M0 P0 修复 — 分区补建
PROMPT ============================================================================
PROMPT 执行前 MUST：
PROMPT 1. 当前登录为 QDITP schema（原业务库）
PROMPT 2. 确认 §2 表空间尚未创建（本包不创建表空间，只补分区）
PROMPT 3. 本包只做两件事：
PROMPT    ① 跑 m0_precheck.sql 查出 15 张分区表的真实分区键 + 最高上界
PROMPT    ② 跑 m0_generate_add_partition.sql 生成 15 个分区 DDL 文件
PROMPT    人工复核后交给 DBA 审批执行
PROMPT ============================================================================

-- =============================================================================
-- Step 1：分区表盘点（7 段查询）
-- 输出到 spool 文件或屏幕，用于人工核对
-- =============================================================================
-- 以下查询代码直接嵌入本文件，避免依赖外部 spool 路径
-- 参考独立文件：docs/ops/environments/scripts/migration/20261014_m0_partition_precheck.sql

PROMPT === 1.1 分区键定义（单列 / 复合列）===
SELECT table_name, partitioning_type, partitioning_key_count, column_name, position
FROM   user_part_key_columns k
JOIN   user_tables t ON t.table_name = k.name
WHERE  t.partitioned = 'YES'
  AND  t.table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
  )
ORDER BY table_name, position
;

PROMPT === 1.2 当前最高分区上界 ===
SELECT p.table_name, p.partition_name, p.partition_position, p.high_value, p.num_rows
FROM   user_tab_partitions p
WHERE  p.table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
)
ORDER BY p.table_name, p.partition_position
;

PROMPT === 1.3 F2F_* GLOBAL 唯一索引（P0-3 陷阱）===
SELECT i.table_name, i.index_name, i.uniqueness, i.partitioned, i.status,
       LISTAGG(c.column_name, ', ') WITHIN GROUP (ORDER BY c.column_position) AS index_columns
FROM   user_indexes i
JOIN   user_ind_columns c ON c.index_name = i.index_name AND c.table_name = i.table_name
WHERE  i.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  i.partitioned = 'NO'
GROUP BY i.table_name, i.index_name, i.uniqueness, i.partitioned, i.status
ORDER BY i.table_name, i.index_name
;

PROMPT === 1.4 F2F_* 主键 / 唯一约束列（判断能否改 LOCAL）===
SELECT uc.constraint_name, uc.table_name, uc.constraint_type,
       LISTAGG(usk.column_name, ', ') WITHIN GROUP (ORDER BY usk.position) AS columns
FROM   user_constraints uc
JOIN   user_cons_columns usk ON usk.constraint_name = uc.constraint_name
WHERE  uc.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  uc.constraint_type IN ('P','U')
GROUP BY uc.constraint_name, uc.table_name, uc.constraint_type
ORDER BY uc.table_name, uc.constraint_name
;

PROMPT === 1.5 分区表表空间 + 高水线 ===
SELECT t.table_name, t.tablespace_name, t.num_rows, t.blocks*8/1024 AS size_mb_approx
FROM   user_tables t
WHERE  t.partitioned = 'YES'
  AND  t.table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
  )
ORDER BY t.num_rows DESC
;

PROMPT === 1.6 RACDG 磁盘组使用率 ===
SELECT name, total_mb, free_mb, round(free_mb/total_mb*100,2) AS free_pct
FROM   v$asm_diskgroup
WHERE  name = 'RACDG'
;

PROMPT === 1.7 分区表段容量 Top20 ===
SELECT s.segment_name, s.partition_name, s.segment_type,
       round(s.bytes/1024/1024,2) AS size_mb
FROM   user_segments s
WHERE  s.segment_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
)
ORDER BY s.bytes DESC
FETCH FIRST 20 ROWS ONLY
;

-- =============================================================================
-- Step 2：生成 2027 分区补建 DDL
-- 输出到 /tmp/qditp_split_ddl/m0_partitions/*.sql（每表一个文件）
-- =============================================================================
-- 以下 PL/SQL 块与原 20261014_m0_generate_add_partition.sql 逻辑一致
-- 直接嵌入本包，无需外部依赖

DECLARE
  v_f   UTL_FILE.FILE_TYPE;
  v_next_month DATE;
  v_part_name  VARCHAR2(30);
  v_tbl        VARCHAR2(128);
  v_ts         VARCHAR2(30);
  v_global_idx BOOLEAN;
BEGIN
  -- 15 张表清单 + 目标表空间 + 是否 GLOBAL 索引
  FOR r IN (
    SELECT 'F2F_ORDER'        n, 'ITP_TXN_TS' ts, TRUE  global_idx FROM dual UNION ALL
    SELECT 'F2F_PAYMENT'      n, 'ITP_TXN_TS' ts, TRUE  global_idx FROM dual UNION ALL
    SELECT 'F2F_RESULT_REPORT' n, 'ITP_TXN_TS' ts, TRUE  global_idx FROM dual UNION ALL
    SELECT 'F2F_REFUND'       n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'F2F_TICKET'       n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'F2F_DEVICE_STATUS' n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'F2F_NOTIFY_TASK'  n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_TXN_DETAIL'      n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'ALIPAY_REFUND_TXN_DETAIL'   n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CALLBACK_LOG'    n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CENTER_MSG_LOG'  n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'GATE_TXN_PAY'        n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'QRCODE_TXN_DETAIL'   n, 'ITP_RIDE_TS' ts, FALSE global_idx FROM dual UNION ALL
    SELECT 'APP_PAY_SIGN_INFO'   n, 'ITP_TXN_TS' ts, FALSE global_idx FROM dual
  ) LOOP
    v_tbl   := r.n;
    v_ts    := r.ts;
    v_global_idx := r.global_idx;

    v_f := UTL_FILE.FOPEN('TMP_DIR', 'M0_' || v_tbl || '_add_2027_partitions.sql', 'W', 32767);

    UTL_FILE.PUT_LINE(v_f, '-- ============================================================');
    UTL_FILE.PUT_LINE(v_f, '-- 表: ' || v_tbl);
    UTL_FILE.PUT_LINE(v_f, '-- 源 schema: QDITP');
    UTL_FILE.PUT_LINE(v_f, '-- 目标表空间: ' || v_ts);
    UTL_FILE.PUT_LINE(v_f, '-- 生成时间: ' || TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS'));
    IF v_global_idx THEN
      UTL_FILE.PUT_LINE(v_f, '-- ⚠️  WARN: 此表带 GLOBAL 唯一索引，分区维护必须带 UPDATE GLOBAL INDEXES');
    END IF;
    UTL_FILE.PUT_LINE(v_f, '-- ============================================================');
    UTL_FILE.NEW_LINE(v_f);

    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.' || v_tbl);
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ' || v_ts);
      IF v_global_idx THEN
        UTL_FILE.PUT_LINE(v_f, '  UPDATE GLOBAL INDEXES;');
      ELSE
        UTL_FILE.PUT_LINE(v_f, ';');
      END IF;
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;

    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('[M0] ' || v_tbl || ' -> M0_' || v_tbl || '_add_2027_partitions.sql');
  END LOOP;

  DBMS_OUTPUT.PUT_LINE('=== M0 分区补建 DDL 生成完成 ===');
  DBMS_OUTPUT.PUT_LINE('共生成 14 个 .sql 文件（APP_PAY_SIGN_INFO 待确认是否为分区表）');
  DBMS_OUTPUT.PUT_LINE('文件位置: $TMP_DIR/M0_*.sql');
END;
/

-- =============================================================================
-- Step 3：复核清单（打印给操作人）
-- =============================================================================
PROMPT ============================================================================
PROMPT 包 1 完成，下一步操作：
PROMPT ============================================================================
PROMPT 1. 在 $TMP_DIR 目录下找到 14 个 M0_*.sql 文件
PROMPT 2. 逐文件人工复核：
PROMPT    - TABLESPACE 是否正确（ITP_TXN_TS / ITP_RIDE_TS）
PROMPT    - 月份范围是否正确（P202701 ~ P202712）
PROMPT    - F2F_ORDER/PAYMENT/RESULT_REPORT 是否带 UPDATE GLOBAL INDEXES
PROMPT 3. 把 14 个 .sql 文件打包，连同本包一起发给 DBA 审批
PROMPT 4. DBA 在维护窗口执行（建议先跑 1 张测试表确认无 ORA-14xxx 错误）
PROMPT 5. 验证：
PROMPT    SELECT table_name, partition_name FROM user_tab_partitions
PROMPT    WHERE table_name IN ('F2F_ORDER','F2F_PAYMENT',...)
PROMPT      AND partition_name LIKE 'P2027%'
PROMPT    ORDER BY table_name, partition_name;
PROMPT 期望：每张表 12 行（P202701 ~ P202712）
PROMPT ============================================================================

COMMIT;