-- 支付宝出行三张表的字段瘦身（2026-09-18，第四轮评估后执行）。
--
-- 本文件是对已存在的库执行的**增量**脚本；`alipay-pay-txn-schema.sql` 已同步成瘦身后的形态，
-- 那份只服务「新建库」。执行前三张表实测均 0 行。
--
-- ============================================================================
-- 一、ALIPAY_PAY_TXN_DETAIL 21 -> 18 列
-- ============================================================================
-- DEBIT_REQUEST_RESULT VARCHAR2(16 CHAR)
--   它是 PAY_STATUS 的派生值，而对外契约那个 debitRequestResult（0/1）实际由主表
--   GATE_TXN_PAY.DEBIT_STATUS 映射。两个状态列并存只带来「MUST 同步回写、漏写就静默不一致」这条
--   本不必存在的护栏 —— pay-sign 侧 2026-08-26 正是漏写它导致「状态已 SUCCESS、扣款结果还停在
--   PROCESSING、APP 长期显示扣费未成功」。删掉列，护栏一并消失。
-- RESPONSE_TIME TIMESTAMP(6)
--   存在价值只是算耗时，而 ALIPAY_PAY_CENTER_MSG_LOG.ELAPSED_MS 已逐次记录，更准也更细。
-- FIRST_REQUEST_TIME TIMESTAMP(6)
--   与 CREATE_TIME 几乎恒等（落单后立即出网），看 CREATE_TIME 即可。
--
-- ============================================================================
-- 二、ALIPAY_REFUND_TXN_DETAIL 26 -> 18 列
-- ============================================================================
-- 【主体已有，去 GATE_TXN_PAY 拿，NEVER 在退款单上复制】
--   THIRD_USER_ID / CARD_ID / CARD_ISSUE_CODE
--   退款单已经有 ORDER_NO，顺着它就能拿到主表那行与支付明细那行。
-- 【四个应答码字段是同一件事的两份，且是覆盖式写入】
--   RET_CODE / RET_MSG（我方码）与 PAY_CENTER_CODE / PAY_CENTER_MSG（支付中心码）
--   退款回查跑 5 轮只剩最后一轮的码 —— 而这类列存在的意义就是留证据。应答已进
--   ALIPAY_PAY_CENTER_MSG_LOG（API_NAME='requestRefund' / 'refundQuery' + REQUEST_NO=退款单号），
--   那里一轮一行、可举证。
-- 【IP_ADDRESS】MSG_LOG 已有，退款单本身不需要。
-- 【保留 OPERATOR】运营手工退款的操作人，审计必需，NEVER 删。
-- 【两处宽度收窄】REFUND_REASON 1024 -> 512、CHANNEL_REFUND_NO 256 -> 128（0 行，无截断风险）。
--
-- 【仍待确认的一列】REFUND_NO 与 REFUND_ORDER_NO、MERCHANT_REFUND_NO 三个退款号并存：
--   REFUND_ORDER_NO 我方生成、MERCHANT_REFUND_NO 送给支付中心（实测退款查询只认它）、
--   REFUND_NO 是支付中心返回的。**如果 MERCHANT_REFUND_NO 是直接拿 REFUND_ORDER_NO 赋值的，
--   那它就是冗余** —— 接线时 MUST 看生成代码再定，NEVER 现在就猜着删。
--
-- 【执行注意】三张表 0 行；DROP COLUMN 与 MODIFY 缩短长度都实测能过 mcp_database_qd 的校验器。
-- 执行后 MUST 回查 USER_TAB_COLS 并把结果写进 ADR。

ALTER TABLE ALIPAY_PAY_TXN_DETAIL DROP (
    DEBIT_REQUEST_RESULT,
    RESPONSE_TIME,
    FIRST_REQUEST_TIME
);

ALTER TABLE ALIPAY_REFUND_TXN_DETAIL DROP (
    THIRD_USER_ID,
    CARD_ID,
    CARD_ISSUE_CODE,
    RET_CODE,
    RET_MSG,
    PAY_CENTER_CODE,
    PAY_CENTER_MSG,
    IP_ADDRESS
);

ALTER TABLE ALIPAY_REFUND_TXN_DETAIL MODIFY (REFUND_REASON VARCHAR2(512 CHAR));

ALTER TABLE ALIPAY_REFUND_TXN_DETAIL MODIFY (CHANNEL_REFUND_NO VARCHAR2(128 CHAR));

COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.PAY_STATUS IS '本笔在支付中心侧的状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭。本表唯一的状态列，原 DEBIT_REQUEST_RESULT 已删除、NEVER 加回；订单整体是否扣费成功以 GATE_TXN_PAY.DEBIT_STATUS 为准';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.LAST_REQUEST_TIME IS '最近一次发起支付的时间；首次时刻看 CREATE_TIME，原 FIRST_REQUEST_TIME 已删除';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.OPERATOR IS '运营手工退款的操作人，审计必需，NEVER 删；应答码与报文原文在 ALIPAY_PAY_CENTER_MSG_LOG';
