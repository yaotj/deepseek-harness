-- ============================================================================
-- 域表 owner 归属回源查询
-- ----------------------------------------------------------------------------
-- 用途：在 AFCITPDB（schema QDITP）执行，产出「表 → 业务域」归属字典，
--       作为 docs/ops/域表owner归属字典.md 的「实时归属」回填数据源。
--
-- 连接：jdbc:oracle:thin:@//172.20.222.3:1521/AFCITPDB  user=qditp
--
-- 重要：
--   1. 本机（办公网）到红岛生产/测试集群的 TCP 被防火墙拦截（仅 ICMP 通），
--      需在集群内可达网段（或经堡垒机）执行本脚本，NEVER 在本机直连跑。
--   2. 第 1 段里的 inferred_domain 是「按表名前缀推导」的约定，不是 DB 事实；
--      真正的归属以业务代码 + 本脚本列出的表/索引清单交叉确认。
--   3. 变更前 MUST 先建 BAK_<表>_<日期> CTAS（见 qd-itp 操作纪律）。
-- ============================================================================

-- 1) 全量表清单 + 注释 + 行数 + 最近分析时间，并附命名前缀推导域
SELECT t.TABLE_NAME,
       c.COMMENTS                                   AS table_comment,
       t.NUM_ROWS,
       t.LAST_ANALYZED,
       CASE
         WHEN t.TABLE_NAME LIKE 'F2F_%'          THEN '当面付 / 扫码取票 (F2F)'
         WHEN t.TABLE_NAME LIKE 'TBL_TVM_%'      THEN '旧 TVM/BOM 订单 (collect-pay 旧表)'
         WHEN t.TABLE_NAME LIKE 'TBL_BOM_%'      THEN '旧 TVM/BOM 订单 (collect-pay 旧表)'
         WHEN t.TABLE_NAME LIKE 'PAY_%'          THEN '支付'
         WHEN t.TABLE_NAME LIKE 'QRCODE_%'       THEN '二维码'
         WHEN t.TABLE_NAME LIKE 'DAILY_TICKET%'  THEN '日次票'
         WHEN t.TABLE_NAME LIKE 'LOGIC_CARD%'    THEN '卡池'
         WHEN t.TABLE_NAME LIKE 'USER_%'         THEN '用户分流'
         WHEN t.TABLE_NAME LIKE 'SYS_%'          THEN 'RuoYi 平台'
         WHEN t.TABLE_NAME LIKE 'AGM_%'          THEN 'AGM 设备'
         WHEN t.TABLE_NAME LIKE 'STATION%'
           OR t.TABLE_NAME LIKE 'TBL_STATION%'   THEN '站点参数'
         ELSE '未分类'
       END                                          AS inferred_domain
FROM   ALL_TABLES t
LEFT JOIN ALL_TAB_COMMENTS c
       ON  c.OWNER = t.OWNER
       AND c.TABLE_NAME = t.TABLE_NAME
       AND c.TABLE_TYPE = 'TABLE'
WHERE  t.OWNER = 'QDITP'
ORDER BY inferred_domain, t.TABLE_NAME;

-- 2) 分区表清单（P0 事实：8 张 RANGE 分区表只到 P202612，2027-01-01 起裁剪/归档失效，
--    须 2026-12-15 前处理）
SELECT TABLE_NAME, PARTITIONED, NUM_ROWS, LAST_ANALYZED
FROM   ALL_TABLES
WHERE  OWNER = 'QDITP'
  AND  PARTITIONED = 'YES'
ORDER BY TABLE_NAME;

-- 3) RANGE 分区表的分区名（确认最大分区是否只到 P202612）
SELECT TABLE_NAME, PARTITION_NAME, PARTITION_POSITION, NUM_ROWS
FROM   ALL_TAB_PARTITIONS
WHERE  TABLE_OWNER = 'QDITP'
ORDER BY TABLE_NAME, PARTITION_POSITION;

-- 4) GLOBAL 索引识别：分区表上 PARTITIONED='NO' 的索引即为 GLOBAL 索引。
--    F2F_* 带 GLOBAL 唯一索引，DROP PARTITION 会致其 UNUSABLE —— 见 qd-itp 操作纪律。
SELECT i.TABLE_NAME, i.INDEX_NAME, i.UNIQUENESS, i.PARTITIONED, i.INDEX_TYPE
FROM   ALL_INDEXES i
JOIN   ALL_TABLES  t
       ON  t.OWNER = i.TABLE_OWNER
       AND t.TABLE_NAME = i.TABLE_NAME
WHERE  i.TABLE_OWNER = 'QDITP'
  AND  t.PARTITIONED = 'YES'
  AND  i.PARTITIONED = 'NO'          -- GLOBAL 索引（LOCAL 索引 PARTITIONED='YES'）
ORDER BY i.TABLE_NAME, i.INDEX_NAME;

-- 5) 各表索引总览（定位唯一索引 / 全局索引，供幂等与分区维护复核）
SELECT i.TABLE_NAME, i.INDEX_NAME, i.UNIQUENESS, i.PARTITIONED, i.STATUS
FROM   ALL_INDEXES i
WHERE  i.TABLE_OWNER = 'QDITP'
ORDER BY i.TABLE_NAME, i.INDEX_NAME;
