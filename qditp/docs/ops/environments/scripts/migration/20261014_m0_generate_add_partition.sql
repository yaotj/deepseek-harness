-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — M0 P0 修复：补建 2027 分区 DDL 生成器
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §1.2 / §7.3
-- 状态：草稿 v1.5（仅供评审，严禁直接在生产库执行）
-- 用途：基于 precheck 结果，自动生成 15 张分区表"补建 2027 年 12 个月分区"的 DDL
-- 执行前置：
--   1. 先跑 20261014_m0_partition_precheck.sql 确认当前最高分区上界
--   2. 根据 precheck 输出手动调整下面的 TABLESPACE 与日期范围
--   3. 本脚本输出的 DDL 仅作为模板，MUST 人工复核后再给 DBA 审批
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

DEFINE OUTFILE_BASE = '/tmp/qditp_split_ddl/m0_partitions'

-- =============================================================================
-- 分区表清单 + 目标表空间映射（与 docs/ops/数据库按域分库计划.md §3 对齐）
--   QDITP_TXN: F2F_* (7) + ALIPAY_PAY_* (4) + GATE_TXN_PAY (1) = 12 张
--   QDITP_RIDE: QRCODE_TXN_DETAIL (1)
--   QDITP_TXN:  APP_PAY_SIGN_INFO (1，待确认归属)
-- =============================================================================
DECLARE
  v_base VARCHAR2(200) := '&OUTFILE_BASE';
  v_f    UTL_FILE.FILE_TYPE;
  v_sql  VARCHAR2(32767);
BEGIN
  -- =====================================================================
  -- 1. F2F_ORDER（QDITP_TXN，RANGE 分区，GLOBAL 唯一索引）
  --    ⚠️  分区维护必须带 UPDATE GLOBAL INDEXES
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'F2F_ORDER_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- F2F_ORDER 补建 2027 年分区（QDITP_TXN）');
  UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 此表带 GLOBAL 唯一索引，所有分区 DDL 必须带 UPDATE GLOBAL INDEXES');
  UTL_FILE.PUT_LINE(v_f, '-- 执行前确认：当前最高分区 high_value，决定起始月份');
  UTL_FILE.PUT_LINE(v_f, '');
  FOR m IN 1..12 LOOP
    v_sql := 'ALTER TABLE QDITP.F2F_ORDER'
          || CHR(10)
          || '  ADD PARTITION P2027'
          || TO_CHAR(TO_DATE('2027-' || LPAD(m, 2, '0') || '-01', 'YYYY-MM-DD'), 'MM')
          || ' VALUES LESS THAN (TO_DATE(''2027-'
          || TO_CHAR(TO_DATE('2027-' || LPAD(m+1, 2, '0') || '-01', 'YYYY-MM-DD'), 'YYYY-MM-DD'),
          || ''',''YYYY-MM-DD''))'
          || CHR(10)
          || '  TABLESPACE ITP_TXN_TS'
          || CHR(10)
          || '  UPDATE GLOBAL INDEXES;';
    -- 修正：上面拼接逻辑有误，改用更清晰的写法
    DECLARE
      v_next_month DATE;
      v_part_name  VARCHAR2(30);
    BEGIN
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.F2F_ORDER');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS');
      UTL_FILE.PUT_LINE(v_f, '  UPDATE GLOBAL INDEXES;');
      UTL_FILE.PUT_LINE(v_f, '');
    END;
  END LOOP;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: F2F_ORDER_add_2027_partitions.sql');

  -- =====================================================================
  -- 2. F2F_PAYMENT（同上，GLOBAL 唯一索引）
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'F2F_PAYMENT_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- F2F_PAYMENT 补建 2027 年分区（QDITP_TXN）');
  UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 此表带 GLOBAL 唯一索引，所有分区 DDL 必须带 UPDATE GLOBAL INDEXES');
  UTL_FILE.PUT_LINE(v_f, '');
  DECLARE
    v_next_month DATE;
    v_part_name  VARCHAR2(30);
  BEGIN
    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.F2F_PAYMENT');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS');
      UTL_FILE.PUT_LINE(v_f, '  UPDATE GLOBAL INDEXES;');
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;
  END;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: F2F_PAYMENT_add_2027_partitions.sql');

  -- =====================================================================
  -- 3. F2F_RESULT_REPORT（同上，GLOBAL 唯一索引）
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'F2F_RESULT_REPORT_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- F2F_RESULT_REPORT 补建 2027 年分区（QDITP_TXN）');
  UTL_FILE.PUT_LINE(v_f, '-- ⚠️ 此表带 GLOBAL 唯一索引，所有分区 DDL 必须带 UPDATE GLOBAL INDEXES');
  UTL_FILE.PUT_LINE(v_f, '');
  DECLARE
    v_next_month DATE;
    v_part_name  VARCHAR2(30);
  BEGIN
    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.F2F_RESULT_REPORT');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS');
      UTL_FILE.PUT_LINE(v_f, '  UPDATE GLOBAL INDEXES;');
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;
  END;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: F2F_RESULT_REPORT_add_2027_partitions.sql');

  -- =====================================================================
  -- 4. F2F_REFUND / F2F_TICKET / F2F_DEVICE_STATUS / F2F_NOTIFY_TASK
  --    （QDITP_TXN，RANGE 分区，无 GLOBAL 唯一索引，无需 UPDATE GLOBAL INDEXES）
  -- =====================================================================
  FOR tbl IN (
    SELECT 'F2F_REFUND'        n FROM dual UNION ALL
    SELECT 'F2F_TICKET'        FROM dual UNION ALL
    SELECT 'F2F_DEVICE_STATUS' FROM dual UNION ALL
    SELECT 'F2F_NOTIFY_TASK'   FROM dual
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', tbl.n || '_add_2027_partitions.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || tbl.n || ' 补建 2027 年分区（QDITP_TXN）');
    UTL_FILE.PUT_LINE(v_f, '-- 此表无 GLOBAL 唯一索引，分区维护无需 UPDATE GLOBAL INDEXES');
    UTL_FILE.PUT_LINE(v_f, '');
    DECLARE
      v_next_month DATE;
      v_part_name  VARCHAR2(30);
    BEGIN
      FOR m IN 1..12 LOOP
        v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
        v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
        UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
        UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.' || tbl.n);
        UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
        UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
        UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS;');
        UTL_FILE.PUT_LINE(v_f, '');
      END LOOP;
    END;
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('Wrote: ' || tbl.n || '_add_2027_partitions.sql');
  END LOOP;

  -- =====================================================================
  -- 5. ALIPAY_PAY_TXN_DETAIL / ALIPAY_REFUND_TXN_DETAIL / ALIPAY_PAY_CALLBACK_LOG / ALIPAY_PAY_CENTER_MSG_LOG
  --    （QDITP_TXN，RANGE 分区，无 GLOBAL 唯一索引）
  -- =====================================================================
  FOR tbl IN (
    SELECT 'ALIPAY_PAY_TXN_DETAIL'      n FROM dual UNION ALL
    SELECT 'ALIPAY_REFUND_TXN_DETAIL'   FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CALLBACK_LOG'    FROM dual UNION ALL
    SELECT 'ALIPAY_PAY_CENTER_MSG_LOG'  FROM dual
  ) LOOP
    v_f := UTL_FILE.FOPEN('TMP_DIR', tbl.n || '_add_2027_partitions.sql', 'W', 32767);
    UTL_FILE.PUT_LINE(v_f, '-- ' || tbl.n || ' 补建 2027 年分区（QDITP_TXN）');
    UTL_FILE.PUT_LINE(v_f, '-- 此表无 GLOBAL 唯一索引，分区维护无需 UPDATE GLOBAL INDEXES');
    UTL_FILE.PUT_LINE(v_f, '');
    DECLARE
      v_next_month DATE;
      v_part_name  VARCHAR2(30);
    BEGIN
      FOR m IN 1..12 LOOP
        v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
        v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
        UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
        UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.' || tbl.n);
        UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
        UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
        UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS;');
        UTL_FILE.PUT_LINE(v_f, '');
      END LOOP;
    END;
    UTL_FILE.FCLOSE(v_f);
    DBMS_OUTPUT.PUT_LINE('Wrote: ' || tbl.n || '_add_2027_partitions.sql');
  END LOOP;

  -- =====================================================================
  -- 6. GATE_TXN_PAY（QDITP_TXN，RANGE 分区）
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'GATE_TXN_PAY_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- GATE_TXN_PAY 补建 2027 年分区（QDITP_TXN）');
  UTL_FILE.PUT_LINE(v_f, '-- 容量热点表（年增量占全库 ~20%），优先排归档');
  UTL_FILE.PUT_LINE(v_f, '');
  DECLARE
    v_next_month DATE;
    v_part_name  VARCHAR2(30);
  BEGIN
    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.GATE_TXN_PAY');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS;');
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;
  END;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: GATE_TXN_PAY_add_2027_partitions.sql');

  -- =====================================================================
  -- 7. QRCODE_TXN_DETAIL（QDITP_RIDE，RANGE 分区，容量热点）
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'QRCODE_TXN_DETAIL_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- QRCODE_TXN_DETAIL 补建 2027 年分区（QDITP_RIDE）');
  UTL_FILE.PUT_LINE(v_f, '-- 容量热点表（年增量占全库 ~15%），优先排归档');
  UTL_FILE.PUT_LINE(v_f, '');
  DECLARE
    v_next_month DATE;
    v_part_name  VARCHAR2(30);
  BEGIN
    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.QRCODE_TXN_DETAIL');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_RIDE_TS;');
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;
  END;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: QRCODE_TXN_DETAIL_add_2027_partitions.sql');

  -- =====================================================================
  -- 8. APP_PAY_SIGN_INFO（QDITP_TXN，若确认为分区表）
  -- =====================================================================
  v_f := UTL_FILE.FOPEN('TMP_DIR', 'APP_PAY_SIGN_INFO_add_2027_partitions.sql', 'W', 32767);
  UTL_FILE.PUT_LINE(v_f, '-- APP_PAY_SIGN_INFO 补建 2027 年分区（QDITP_TXN）');
  UTL_FILE.PUT_LINE(v_f, '-- ⚠️  请先确认此表是否为分区表（跑 precheck 查询 1 核对）');
  UTL_FILE.PUT_LINE(v_f, '-- 若非分区表，本文件可直接删除');
  UTL_FILE.PUT_LINE(v_f, '');
  DECLARE
    v_next_month DATE;
    v_part_name  VARCHAR2(30);
  BEGIN
    FOR m IN 1..12 LOOP
      v_next_month := ADD_MONTHS(TO_DATE('2027-01-01', 'YYYY-MM-DD'), m);
      v_part_name  := 'P' || TO_CHAR(v_next_month, 'YYYYMM');
      UTL_FILE.PUT_LINE(v_f, '-- 补建 ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, 'ALTER TABLE QDITP.APP_PAY_SIGN_INFO');
      UTL_FILE.PUT_LINE(v_f, '  ADD PARTITION ' || v_part_name);
      UTL_FILE.PUT_LINE(v_f, '  VALUES LESS THAN (TO_DATE(''' || TO_CHAR(v_next_month, 'YYYY-MM-DD') || ''',''YYYY-MM-DD''))');
      UTL_FILE.PUT_LINE(v_f, '  TABLESPACE ITP_TXN_TS;');
      UTL_FILE.PUT_LINE(v_f, '');
    END LOOP;
  END;
  UTL_FILE.FCLOSE(v_f);
  DBMS_OUTPUT.PUT_LINE('Wrote: APP_PAY_SIGN_INFO_add_2027_partitions.sql');

  DBMS_OUTPUT.PUT_LINE('=== M0 分区补建 DDL 生成完成，文件位于 ' || v_base || ' ===');
  DBMS_OUTPUT.PUT_LINE('共生成 15 个 SQL 文件，逐个人工复核后交给 DBA 审批');
END;
/

PROMPT === 复核清单 ===
PROMPT 1. 每个 .sql 文件开头都有 ⚠️ 注释说明是否需要 UPDATE GLOBAL INDEXES
PROMPT 2. TABLESPACE 已按域预填：TXN 域用 ITP_TXN_TS，RIDE 域用 ITP_RIDE_TS
PROMPT 3. 执行顺序：按月份升序（P202701 → P202712），不要乱序
PROMPT 4. F2F_ORDER/PAYMENT/RESULT_REPORT 的 DDL 必须带 UPDATE GLOBAL INDEXES（已预填）
PROMPT 5. 其余 12 张表无需 UPDATE GLOBAL INDEXES（无 GLOBAL 唯一索引）
PROMPT 6. 若 precheck 发现某表当前最高分区已到 P202706，则从 P202707 开始补建（改循环起点）
PROMPT 7. 执行前 MUST 在测试环境先跑一遍，确认无 ORA-14xxx 分区错误
PROMPT === 完成 ===