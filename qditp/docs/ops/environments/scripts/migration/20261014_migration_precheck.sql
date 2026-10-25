-- =============================================================================
-- 青岛地铁 ITP 数据库按域分库 — 迁移前自检 SQL
-- 配套文档：docs/ops/数据库按域分库迁移DDL草案.md §3
-- 状态：草稿 v1.3（仅供评审，严禁直接在生产库执行）
-- 执行时机：M0 之前，在 QDITP schema 下跑，确认现有对象基线
-- =============================================================================

SET VERIFY OFF
SET FEEDBACK 1
SET LINESIZE 200
SET PAGESIZE 100
SET SERVEROUTPUT ON SIZE UNLIMITED

PROMPT === 1. 当前 QDITP schema 下表数（期望 130）===
SELECT COUNT(*) AS table_count FROM user_tables
;

PROMPT === 2. 分区表清单（15 张，需确认分区上界与 GLOBAL 索引）===
SELECT t.table_name, t.num_rows, t.blocks, t.last_analyzed
FROM   user_tables t
WHERE  t.partitioned = 'YES'
ORDER BY t.table_name
;

PROMPT === 3. 分区表各分区上界（确认哪些只建到 P202612）===
SELECT table_name, partition_name, partition_position, high_value
FROM   user_tab_partitions
WHERE  table_name IN (
    'F2F_ORDER','F2F_PAYMENT','F2F_REFUND','F2F_RESULT_REPORT',
    'F2F_TICKET','F2F_DEVICE_STATUS','F2F_NOTIFY_TASK',
    'GATE_TXN_PAY','QRCODE_TXN_DETAIL',
    'ALIPAY_PAY_TXN_DETAIL','ALIPAY_REFUND_TXN_DETAIL',
    'ALIPAY_PAY_CALLBACK_LOG','ALIPAY_PAY_CENTER_MSG_LOG',
    'APP_PAY_SIGN_INFO'
)
ORDER BY table_name, partition_position
;

PROMPT === 4. F2F_* GLOBAL 唯一索引（P0-3 陷阱，需裁定是否改 LOCAL）===
SELECT i.table_name, i.index_name, i.uniqueness, i.partitioned, i.status
FROM   user_indexes i
WHERE  i.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  i.partitioned = 'NO'   -- GLOBAL 索引
ORDER BY i.table_name, i.index_name
;

PROMPT === 5. F2F_* 唯一约束列（判断能否加分区键改 LOCAL）===
SELECT uc.constraint_name, uc.table_name, uc.constraint_type, uc.status
FROM   user_constraints uc
WHERE  uc.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  uc.constraint_type = 'P'   -- PRIMARY KEY
UNION ALL
SELECT uc.constraint_name, uc.table_name, uc.constraint_type, uc.status
FROM   user_constraints uc
WHERE  uc.table_name IN ('F2F_ORDER','F2F_PAYMENT','F2F_RESULT_REPORT')
  AND  uc.constraint_type = 'U'   -- UNIQUE
ORDER BY 2, 1
;

PROMPT === 6. 各表序列（若有）===
SELECT sequence_name, last_number
FROM   user_sequences
WHERE  sequence_name LIKE 'SEQ_%'
   OR  sequence_name LIKE '%_SEQ'
ORDER BY sequence_name
;

PROMPT === 7. 各表触发器（若有）===
SELECT trigger_name, table_name, status, triggering_event, command_type
FROM   user_triggers
WHERE  table_name IN (
    SELECT table_name FROM user_tables
)
ORDER BY table_name, trigger_name
;

PROMPT === 8. 跨表外键（迁后需重新指向目标 schema 或经同义词）===
SELECT a.constraint_name, a.table_name, a.r_constraint_name, b.table_name AS ref_table
FROM   user_constraints a
JOIN   user_constraints b ON a.r_constraint_name = b.constraint_name
WHERE  a.constraint_type = 'R'
ORDER BY a.table_name, a.constraint_name
;

PROMPT === 9. RACDG 磁盘组当前使用率（容量 P0-4 检查）===
SELECT name, total_mb, free_mb, round(free_mb/total_mb*100,2) AS free_pct
FROM   v$asm_diskgroup
WHERE  name = 'RACDG'
;

PROMPT === 10. 当前 RACDG 上 ITP 相关段容量 Top20（确认容量热点）===
SELECT segment_name, segment_type, round(bytes/1024/1024,2) AS size_mb
FROM   user_segments
ORDER BY bytes DESC
FETCH FIRST 20 ROWS ONLY
;

PROMPT === 自检完成，打印基线后手动对比后续迁移结果 ===
COMMIT;