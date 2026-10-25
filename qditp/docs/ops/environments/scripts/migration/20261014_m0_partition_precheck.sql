-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — M0 P0 修复：分区表盘点（增强版）
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §1
-- 状态：草稿 v1.5（仅供评审，严禁直接在生产库执行）
-- 用途：在 QDITP schema 下跑，查出 15 张分区表的：
--       ① 分区键（单列 / 复合列）
--       ② 当前最高分区名称 + high_value
--       ③ 是否 GLOBAL 唯一索引（P0-3 陷阱）
--       ④ 建议下一步补建分区的月份
-- 执行前置：当前登录为 QDITP schema
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

-- =============================================================================
-- 15 张分区表清单（与 docs/ops/数据库按域分库计划.md §3 对齐）
--   face-pay 7 张（全 RANGE 分区，前 3 张带 GLOBAL 唯一索引）
--   alipay-pay-sign 4 张（RANGE 分区）
--   gate-txn-pay 1 张（RANGE 分区）
--   ticket-server 1 张（RANGE 分区）
--   pay-sign 1 张（RANGE 分区，待确认）
-- =============================================================================
DEFINE PARTITION_TABLES = qquote$
F2F_ORDER,F2F_PAYMENT,F2F_REFUND,F2F_RESULT_REPORT,
F2F_TICKET,F2F_DEVICE_STATUS,F2F_NOTIFY_TASK,
ALIPAY_PAY_TXN_DETAIL,ALIPAY_REFUND_TXN_DETAIL,
ALIPAY_PAY_CALLBACK_LOG,ALIPAY_PAY_CENTER_MSG_LOG,
GATE_TXN_PAY,QRCODE_TXN_DETAIL,APP_PAY_SIGN_INFO
qquote$

-- =============================================================================
-- 查询 1：每张表的分区键（单列 / 复合列）
-- =============================================================================
PROMPT === 1. 分区键定义 ===
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

-- =============================================================================
-- 查询 2：每张表的当前最高分区（名称 + high_value + 分区位置）
-- =============================================================================
PROMPT === 2. 当前最高分区上界 ===
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

-- =============================================================================
-- 查询 3：F2F_* GLOBAL 唯一索引（P0-3 陷阱）
-- =============================================================================
PROMPT === 3. F2F_* GLOBAL 唯一索引（需裁定改 LOCAL 还是迁移期兜底 UPDATE GLOBAL INDEXES）===
SELECT i.table_name, i.index_name, i.uniqueness, i.partitioned, i.status,
       LISTAGG(c.column_name, ', ') WITHIN GROUP (ORDER BY c.column_position) AS index_columns
FROM   user_indexes i
JOIN   user_ind_columns c ON c.index_name = i.index_name AND c.table_name = i.table_name
WHERE  i.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  i.partitioned = 'NO'   -- GLOBAL
GROUP BY i.table_name, i.index_name, i.uniqueness, i.partitioned, i.status
ORDER BY i.table_name, i.index_name
;

-- =============================================================================
-- 查询 4：F2F_* 唯一约束列（判断能否加分区键改 LOCAL）
-- =============================================================================
PROMPT === 4. F2F_* 主键 / 唯一约束列 ===
SELECT uc.constraint_name, uc.table_name, uc.constraint_type,
       LISTAGG(usk.column_name, ', ') WITHIN GROUP (ORDER BY usk.position) AS columns
FROM   user_constraints uc
JOIN   user_cons_columns usk ON usk.constraint_name = uc.constraint_name
WHERE  uc.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  uc.constraint_type IN ('P','U')
GROUP BY uc.constraint_name, uc.table_name, uc.constraint_type
ORDER BY uc.table_name, uc.constraint_name
;

-- =============================================================================
-- 查询 5：各分区表的表空间 + 高水线（评估是否需要多盘均衡）
-- =============================================================================
PROMPT === 5. 分区表所在表空间 + 高水线 ===
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

-- =============================================================================
-- 查询 6：RACDG 当前使用率（容量 P0-4）
-- =============================================================================
PROMPT === 6. RACDG 磁盘组使用率 ===
SELECT name, total_mb, free_mb, round(free_mb/total_mb*100,2) AS free_pct
FROM   v$asm_diskgroup
WHERE  name = 'RACDG'
;

-- =============================================================================
-- 查询 7：分区表在 RACDG 上的段大小（Top20）
-- =============================================================================
PROMPT === 7. 分区表段容量 Top20（确认容量热点）===
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
-- 汇总：自动推断下一步该补建的分区
-- =============================================================================
PROMPT === 8. 建议补建分区汇总（基于当前最高 high_value 推算）===
BEGIN
  FOR r IN (
    SELECT p.table_name, p.partition_name, p.high_value, p.partition_position
    FROM   user_tab_partitions p
    WHERE  p.table_name IN (
      'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
      'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
      'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
      'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
      'GATE_TXN_PAY','QRCODE_TXN_DETAIL','APP_PAY_SIGN_INFO'
    )
    ORDER BY p.table_name, p.partition_position
  ) LOOP
    -- 简单推断：high_value 是 TO_DATE('YYYY-MM-DD') 格式时，下个月补建
    -- 实际执行前 MUST 人工核对 high_value 原始文本
    DBMS_OUTPUT.PUT_LINE(
      r.table_name || ' 当前最高分区: ' || r.partition_name || '  high_value: ' || r.high_value
    );
  END LOOP;
END;
/

PROMPT === M0 修复建议 ===
PROMPT 1. 所有 15 张分区表 MUST 补建至少 P202701 ~ P202712（留 2 周观察窗口到 2026-12-15）
PROMPT 2. F2F_ORDER/PAYMENT/RESULT_REPORT 若唯一键不含分区键，MUST 保留 GLOBAL 索引，
PROMPT    迁移期所有分区 DDL 必须带 UPDATE GLOBAL INDEXES（见草案 §1.3 / §7.3）
PROMPT 3. 若唯一键含分区键，可删旧 GLOBAL 重建 LOCAL UNIQUE（风险高，MUST DBA 裁定）
PROMPT 4. 补建分区时 TABLESPACE 用目标库的表空间（ITP_TXN_TS / ITP_RIDE_TS 等）
PROMPT 5. 生成具体 DDL 用下方 @20261014_generate_add_partition.sql
PROMPT === 完成 ===
COMMIT;