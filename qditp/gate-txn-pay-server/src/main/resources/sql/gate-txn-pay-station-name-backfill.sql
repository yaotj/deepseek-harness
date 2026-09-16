-- GATE_TXN_PAY 两个中文站名列的存量数据补齐（ADR-D83 的存量侧配套）
--
-- 背景：ADR-D83 之前，ENTRY_STATION_NAME / EXIT_STATION_NAME 的唯一写入源是 ticket-server
-- 的 fillStationNames，取值来自被无条件覆盖过的 lastHandleStationCode（占位值 FFFF），
-- 而权威的离线码进站编码要等 gate-txn-pay-server 的 FareCalculator 重查后才写进 IN_STATION。
-- 于是历史行可能出现两种形态：①站名为空（列表侧回落显示站点编码，即用户看到的 0622）；
-- ②站名非空但与本行编码不同源（名是 FFFF 时代的，码是重查后的）。
-- 代码侧已于 2.0.74 修好，本脚本只负责已经落库的存量行。
--
-- 站名的唯一权威来源是当前路网参数版本：
--   TBL_STATION_INFO.STATION_NM WHERE PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
-- 注意列名是 STATION_NM 不是 STATION_NAME（写 STATION_NAME 会被 MCP 报成通用的 cast 错误，看不出是列不存在）。
--
-- 执行顺序 MUST 是 1 -> 2 -> 3；第 4 步是**独立决策**，NEVER 与第 2 步一起跑。
-- 2026-09-15 在 AFCITPDB 实跑：第 1 步与第 4 步的盘点 SQL 都是 0 行（64 行全部两列齐备且与 v41 一致），
-- 因此第 2 步当时是 no-op。**这不代表脚本没用** —— 换库或换账期后 MUST 重新跑第 1 步再判断。

-- 1. 盘点：有多少行缺站名、其中多少能按当前参数版本解析出来。先看数，再决定要不要改。
SELECT COUNT(*)                                                                     AS TOTAL_ROWS,
       COUNT(CASE WHEN ENTRY_STATION_NAME IS NULL THEN 1 END)                       AS ENTRY_NAME_NULL,
       COUNT(CASE WHEN EXIT_STATION_NAME IS NULL THEN 1 END)                        AS EXIT_NAME_NULL,
       COUNT(CASE WHEN IN_STATION = 'FFFF' OR OUT_STATION = 'FFFF' THEN 1 END)       AS PLACEHOLDER_CODE_ROWS
FROM GATE_TXN_PAY;

-- 2. 只补空值。两条 UPDATE 都带 IS NULL 前置，因此可重复执行；解析不到站名的行原样保留 NULL。
--    NEVER 去掉 IS NULL 条件 —— 那会把非空站名一起覆盖，而原值无处可查、不可回滚。
--    NEVER 用 NVL(..., IN_STATION) 之类的兜底把编码写进名字列 —— 列表侧本来就有回落逻辑，
--    把编码写进名字列等于让「有没有真站名」这件事再也分辨不出来。
UPDATE GATE_TXN_PAY g
SET g.ENTRY_STATION_NAME = (SELECT s.STATION_NM
                            FROM TBL_STATION_INFO s
                            WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                              AND s.STATION_CODE = g.IN_STATION)
WHERE g.ENTRY_STATION_NAME IS NULL
  AND EXISTS (SELECT 1
              FROM TBL_STATION_INFO s
              WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                AND s.STATION_CODE = g.IN_STATION);

UPDATE GATE_TXN_PAY g
SET g.EXIT_STATION_NAME = (SELECT s.STATION_NM
                           FROM TBL_STATION_INFO s
                           WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                             AND s.STATION_CODE = g.OUT_STATION)
WHERE g.EXIT_STATION_NAME IS NULL
  AND EXISTS (SELECT 1
              FROM TBL_STATION_INFO s
              WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                AND s.STATION_CODE = g.OUT_STATION);

COMMIT;

-- 3. 回查：期望 ENTRY_NAME_NULL / EXIT_NAME_NULL 只剩「编码在当前参数版本里查不到」的那部分。
--    UNRESOLVABLE_* 就是这部分的上限，两者相等即说明没有漏补。
SELECT COUNT(CASE WHEN ENTRY_STATION_NAME IS NULL THEN 1 END) AS ENTRY_NAME_NULL,
       COUNT(CASE WHEN EXIT_STATION_NAME IS NULL THEN 1 END)  AS EXIT_NAME_NULL,
       COUNT(CASE WHEN NOT EXISTS (SELECT 1 FROM TBL_STATION_INFO s
                                   WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                                     AND s.STATION_CODE = g.IN_STATION) THEN 1 END)  AS UNRESOLVABLE_ENTRY_CODE,
       COUNT(CASE WHEN NOT EXISTS (SELECT 1 FROM TBL_STATION_INFO s
                                   WHERE s.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                                     AND s.STATION_CODE = g.OUT_STATION) THEN 1 END) AS UNRESOLVABLE_EXIT_CODE
FROM GATE_TXN_PAY g;

-- 4. 名与码不同源的行：**只盘点、不自动改**。这类行站名非空，看不出对不对，但它可能是 FFFF 时代
--    写下的旧名 + 重查后的新码。改它等于覆盖非空业务数据、原值无处可查，因此 MUST 先人工核对
--    OFFLINE_FLAG / IN_TIME 与 QRCODE_TXN_DETAIL 的进站行，确认「码是对的、名是旧的」再逐单改，
--    并在改之前 CREATE TABLE ... AS SELECT 留一份快照。NEVER 把下面这条盘点 SQL 直接改成 UPDATE。
SELECT g.ORDER_NO, g.TXN_DATE, g.OFFLINE_FLAG,
       g.IN_STATION, g.ENTRY_STATION_NAME, e.STATION_NM AS ENTRY_NAME_EXPECTED,
       g.OUT_STATION, g.EXIT_STATION_NAME, x.STATION_NM AS EXIT_NAME_EXPECTED
FROM GATE_TXN_PAY g
         LEFT JOIN TBL_STATION_INFO e
                   ON e.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                       AND e.STATION_CODE = g.IN_STATION
         LEFT JOIN TBL_STATION_INFO x
                   ON x.PARA_VER_NO = (SELECT CURRENT_VER_NO FROM TBL_PARA_VERSION WHERE PARA_TYPE = '0001')
                       AND x.STATION_CODE = g.OUT_STATION
WHERE (e.STATION_NM IS NOT NULL AND g.ENTRY_STATION_NAME IS NOT NULL AND g.ENTRY_STATION_NAME <> e.STATION_NM)
   OR (x.STATION_NM IS NOT NULL AND g.EXIT_STATION_NAME IS NOT NULL AND g.EXIT_STATION_NAME <> x.STATION_NM)
ORDER BY g.TXN_DATE, g.ORDER_NO;
