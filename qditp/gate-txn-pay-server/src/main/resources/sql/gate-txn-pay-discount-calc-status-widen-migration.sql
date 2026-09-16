-- GATE_TXN_PAY.DISCOUNT_CALC_STATUS 加宽 16 -> 32 字符
--
-- 背景（2026-09-14 实测）：离线码出站在 FareCalculator.calculateOfflineFare 算不出票价时，
-- GateTxnPayServiceImpl.saveOfflineFarePendingOrder 会落一条「待重算」订单留痕，
-- 该分支写入 DISCOUNT_CALC_STATUS = 'OFFLINE_FARE_PENDING'（20 字符），
-- 而本列原为 VARCHAR2(16 CHAR)，于是整条 INSERT 被 Oracle 拒绝：
--   ORA-12899: 列 "QDITP"."GATE_TXN_PAY"."DISCOUNT_CALC_STATUS" 的值太大 (实际值: 20, 最大值: 16)
-- 后果是「落单留痕待补偿」这条最后防线 100% 失效 —— 本次出站在 GATE_TXN_PAY 零痕迹，
-- OfflineFareRecoveryProcessor 靠 DEBIT_STATUS='INIT' AND DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'
-- 扫表，行都没落进去，补偿永远扫不到、无法自愈。
-- 实测样本：卡号 ...095 在 18:28:44 与 18:30:23 两笔离线码出站，闸机侧已放行（ticket-server 返 0000、
-- QRCODE_STATUS 已推进 81 -> 05），但 GATE_TXN_PAY 当日 0 行。
--
-- 为什么改列宽而不改常量：'OFFLINE_FARE_PENDING' 这个字面量在 GateTxnPayMapper.xml 里有 3 处硬编码
-- （:585 / :618 / :632 的 WHERE 条件；:612 的 SET 是 #{discountCalcStatus} 参数、不含字面量），
-- 改常量要同步这 3 处 WHERE 加 Java 侧写入点，漏一处即「重算捞不到」或「重算完状态对不上」；
-- 加宽列不需要改代码、不需要重建镜像、不需要重启，且对已有数据无损。
--
-- 回退语句（仅在本列无超过 16 字符的数据时才能成功，NEVER 在已产生 OFFLINE_FARE_PENDING 后回退）：
--   ALTER TABLE GATE_TXN_PAY MODIFY (DISCOUNT_CALC_STATUS VARCHAR2(16 CHAR));

ALTER TABLE GATE_TXN_PAY MODIFY (DISCOUNT_CALC_STATUS VARCHAR2(32 CHAR));

COMMENT ON COLUMN GATE_TXN_PAY.DISCOUNT_CALC_STATUS IS 'SUCCESS/FALLBACK/SKIPPED/OFFLINE_FARE_PENDING';

-- 回查（2026-09-14 在 AFCITPDB 执行后实测 CHAR_LENGTH=32、DATA_LENGTH=64）：
--
-- SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH
-- FROM USER_TAB_COLS
-- WHERE TABLE_NAME = 'GATE_TXN_PAY'
--   AND COLUMN_NAME = 'DISCOUNT_CALC_STATUS';
