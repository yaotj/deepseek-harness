-- ALIPAY_PAY_CALLBACK_LOG 结构收敛（2026-09-18 定稿）。本文件收敛出 14 列，**库内实际是 17 列**：
-- 退款三列 REFUND_ORDER_NO / REFUND_AMOUNT / REFUND_STATUS 由 alipay-pay-callback-log-extend-migration.sql
-- 第二批加入，**本文件的 DROP 清单刻意不含它们** —— 那三列服务 CALLBACK_TYPE=REFUND 的证据行，
-- 与支付方向的列按 CALLBACK_TYPE 分组、互不覆盖。NEVER 据本文件的清单把列数推断成 14。
--
-- 本文件记录该表在 2026-09-18 这一天的全部结构变动，**按执行顺序写、可原样重跑一次**（表 0 行）。
--
-- ============================================================================
-- 一、删 10 列：这批字段在支付宝出行链路里没有入向来源，或是派生值 / 主体已有
-- ============================================================================
-- 【8 个无入向来源，NEVER 加回】
--   MERCHANT_ORDER_NO / CHANNEL_ORDER_NO / PAY_USER_ID / PAY_TIME
--   CASH_AMOUNT / COUPON_AMOUNT / DISCOUNT_FEE / DISCOUNT_INFO
-- 判据（2026-09-18 逐字段核对代码与供方文档）：
--   支付结果回调 DTO（model 的 AlipayTripPayNotifyReqDTO）**只有 6 个业务字段** ——
--     orderNo / channelVoucherId / transAmount / transTime / transStatus / cardNo
--   requestPay 的同步应答，代码只取 channelOrderNo（且不落库）
--   payQuery 的同步应答，代码只取 4 个键（tradeNo / totalAmount / paymentTime / tradeStatus）
-- 也就是说这 8 列**建了也永远是 NULL** —— 那不是留痕，是白占列宽与阅读注意力。
-- ⚠️ 与 pay-sign 的差异 MUST 记住：pay-sign 的 ReceivePayResultReqDTO 有 12 个字段（含
-- cashAmount / couponAmount / payUserId / discountInfo），它的 PAY_CALLBACK_LOG 因此有 24 列。
-- **NEVER 因为那边有就照抄到支付宝渠道** —— 两条回调契约不是一回事。哪天支付中心扩了契约，
-- MUST 按当时的**真实报文**加列，NEVER 照供方文档预置。
-- 【另 2 列】
--   PAY_STATUS —— 是 TRANS_STATUS 的映射结果（1 -> SUCCESS、2 -> FAIL）。流水表存原文就够，
--                 派生值用时再算；留着等于同一事实两处存储。
--   CARD_NO    —— 主体 GATE_TXN_PAY.CARD_ID 已有，且报文原文在 RAW_BODY 里。
--
-- ============================================================================
-- 二、补 2 列
-- ============================================================================
--   TOTAL_AMOUNT NUMBER(22)   —— 上面那批里唯一有过取值点的：payQuery 同步应答里取 "totalAmount"。
--                                NUMBER 且单位为分，与本表 TRANS_AMOUNT（支付宝报文原文、VARCHAR2）
--                                **不同口径、NEVER 合并**。
--   TXN_DATE VARCHAR2(8 CHAR) —— 订单日期 yyyyMMdd，取自主表，供按账期检索。
--                                本表**未分区**，NEVER 把它当分区键用。
--
-- ============================================================================
-- 三、RAW_BODY 从 VARCHAR2(4000) 改 CLOB：MUST 用 DROP + ADD，NEVER 用 MODIFY
-- ============================================================================
-- Oracle 不允许把 VARCHAR2 直接 MODIFY 成 CLOB，即使表是空的 —— 2026-09-18 实测报
-- **ORA-22859: invalid modification of columns**。本表 0 行，DROP 掉再 ADD 成 CLOB 无数据损失，
-- 唯一副作用是该列的 COLUMN_ID 挪到末尾（mapper 显式写列名，不受影响）。
-- 改 CLOB 的理由：原写入点靠 truncate(...,4000) 硬截，而**截断后的报文不再是可举证的原文**，
-- 出问题时无法与对方对账。改完后写入点已去掉那个截断。
--
-- ============================================================================
-- 四、本表定位（四张表职责 NEVER 混）
-- ============================================================================
--   GATE_TXN_PAY                订单主表，扣费成没成的唯一权威
--   ALIPAY_PAY_TXN_DETAIL       支付侧当前态，一单一行，有唯一索引
--   ALIPAY_PAY_CENTER_MSG_LOG   我方 -> 支付中心，一次调用一行
--   ALIPAY_PAY_CALLBACK_LOG     支付中心 -> 我方，一次推送一行（本表），刻意无唯一索引
-- 本表回答「对方第 N 次推送时说了什么、我处理成没成」。判断订单当前状态 MUST 查明细表，
-- NEVER 在本表按时间取最后一行推断。
--
-- 【执行注意】本表 0 行；执行后 MUST 回查 USER_TAB_COLS 并把结果写进 ADR。

ALTER TABLE ALIPAY_PAY_CALLBACK_LOG DROP (
    MERCHANT_ORDER_NO,
    CHANNEL_ORDER_NO,
    PAY_USER_ID,
    PAY_TIME,
    CASH_AMOUNT,
    COUPON_AMOUNT,
    DISCOUNT_FEE,
    DISCOUNT_INFO,
    PAY_STATUS,
    CARD_NO
);

ALTER TABLE ALIPAY_PAY_CALLBACK_LOG DROP (RAW_BODY);

ALTER TABLE ALIPAY_PAY_CALLBACK_LOG ADD (
    RAW_BODY      CLOB,
    TOTAL_AMOUNT  NUMBER(22),
    TXN_DATE      VARCHAR2(8 CHAR)
);

COMMENT ON COLUMN ALIPAY_PAY_CALLBACK_LOG.RAW_BODY IS '回调整包报文原文；2026-09-18 由 VARCHAR2(4000) 改为 CLOB，截断后的报文不再可举证';
COMMENT ON COLUMN ALIPAY_PAY_CALLBACK_LOG.TOTAL_AMOUNT IS '支付中心口径的订单总金额（分），来自 payQuery 同步应答的 totalAmount 键；与本表 TRANS_AMOUNT（支付宝报文原文字符串）不是同一口径，NEVER 合并';
COMMENT ON COLUMN ALIPAY_PAY_CALLBACK_LOG.TXN_DATE IS '订单日期 yyyyMMdd，取自主表 GATE_TXN_PAY.TXN_DATE，供按账期检索；本表当前未分区，NEVER 把它当分区键用';
COMMENT ON COLUMN ALIPAY_PAY_CALLBACK_LOG.TRANS_STATUS IS '支付宝报文原始交易状态，1成功 2失败；映射后的 SUCCESS/FAIL 是派生值，刻意不落库';
