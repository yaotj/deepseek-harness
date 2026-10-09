-- ============================================================================
-- ALIPAY_PAY_TXN_DETAIL.TRANS_TIME 存量归一：统一成 14 位 yyyyMMddHHmmss
--
-- 背景（2026-09-20，用户裁决「这一列按一个格式统一」）：
--   该列 2026-09-18 16:15:06 加入时的口径是「支付中心回调报文 transTime 原文直存」，
--   而原文格式不统一 —— 实测同时出现 19 位 '2026-09-18 16:44:41' 与 14 位 '20260918021500'。
--   对外契约（IF8A-05 / IF8A-34 的 payOrderNoDate、日票回填、同响应里的 entryDate/exitDate）
--   全仓都是 14 位 yyyyMMddHHmmss，于是每个读取方都得各自再归一一次，漏一处就把 19 位发出去。
--   因此改为「写入侧归一」：alipay-pay-sign-server 1.1.43 的
--   PayTxnCallbackWriter.normalizeTransTime 负责剥非数字取前 14 位，本脚本把存量行补齐。
--   1.1.43 已部署并端到端验证：订单 GT20260920101619588542741 的回调台账仍是 19 位
--   '2026-09-20 10:16:23'，而明细行落库即 '20260920101623'（LEN=14）。
--
-- 报文原文不会因此丢失：ALIPAY_PAY_CALLBACK_LOG 的 TRANS_TIME 与 RAW_BODY 是回调台账、仍存原文。
-- NEVER 反过来去归一那张表 —— 举证要原文。
--
-- 幂等：WHERE 只挑「剥掉非数字后位数 >= 14 且当前值与归一结果不同」的行，重复执行影响 0 行。
-- 数字位数不足 14 的行不动（原样保留），与 Java 侧「不足 14 位原样入库 + WARN」一致：
--   NEVER 补零（月日被补成 01 会造出看着合法的假时间）。
--
-- 执行前原值（2026-09-20 现查，全表仅这 2 行非空，其余 3 行本就是 NULL）：
--   GT20260918164438714542741  '2026-09-18 16:44:41'
--   GT20260920100237056542741  '2026-09-20 10:02:44'
-- 还原 SQL（只在需要回退本次归一时用）：
--   UPDATE ALIPAY_PAY_TXN_DETAIL SET TRANS_TIME = '2026-09-18 16:44:41'
--    WHERE ORDER_NO = 'GT20260918164438714542741';
--   UPDATE ALIPAY_PAY_TXN_DETAIL SET TRANS_TIME = '2026-09-20 10:02:44'
--    WHERE ORDER_NO = 'GT20260920100237056542741';
--   COMMIT;
-- ============================================================================

UPDATE ALIPAY_PAY_TXN_DETAIL
   SET TRANS_TIME = SUBSTR(REGEXP_REPLACE(TRANS_TIME, '[^0-9]', ''), 1, 14)
 WHERE TRANS_TIME IS NOT NULL
   AND LENGTH(REGEXP_REPLACE(TRANS_TIME, '[^0-9]', '')) >= 14
   AND TRANS_TIME <> SUBSTR(REGEXP_REPLACE(TRANS_TIME, '[^0-9]', ''), 1, 14);

COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.TRANS_TIME IS '支付时刻，统一存14位yyyyMMddHHmmss（2026-09-20起，归一在PayTxnCallbackWriter.normalizeTransTime做）；报文原文留在ALIPAY_PAY_CALLBACK_LOG的TRANS_TIME与RAW_BODY；列仍是VARCHAR2因归一失败时原样入库，NEVER建成DATE也NEVER用TO_DATE查询；支付宝出行记录应答的payOrderNoDate取此列；只由支付回调写入且套NVL';

COMMIT;
