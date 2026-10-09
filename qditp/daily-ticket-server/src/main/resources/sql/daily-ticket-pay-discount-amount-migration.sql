-- 日票 / 旅游票支付回调优惠字段落库迁移（2026-09-20）
-- 背景：支付中心支付结果回调的 bizData 带 cashAmount / couponAmount / totalAmount 三个金额，
--       此前我方只把 cashAmount 当 payAmount 的首选兜底值，couponAmount 完全没有读点，
--       两个字段都不落库、只以原文留在 DAILY_TICKET_PAY_LOG.REQUEST_BODY（CLOB）里。
-- 口径：原样照写，NEVER 加「cash + coupon 是否等于 total」这类匹配校验（与支付域既有裁决一致）；
--       回写走 NVL 保护，失败分支与主动查询分支不带值时不会把已落的金额抹成 NULL。
-- 执行环境：AFCITPDB（172.20.222.3:1521，用户 qditp）

ALTER TABLE DAILY_TICKET_ORDER ADD (CASH_AMOUNT NUMBER(12));
ALTER TABLE DAILY_TICKET_ORDER ADD (COUPON_AMOUNT NUMBER(12));

COMMENT ON COLUMN DAILY_TICKET_ORDER.CASH_AMOUNT IS '支付中心回调回传的实付现金金额（分）；原样落库，不做与总额的匹配校验';
COMMENT ON COLUMN DAILY_TICKET_ORDER.COUPON_AMOUNT IS '支付中心回调回传的优惠金额（分）；原样落库，不做与总额的匹配校验';

ALTER TABLE TRAVEL_TICKET_ORDER ADD (CASH_AMOUNT NUMBER(12));
ALTER TABLE TRAVEL_TICKET_ORDER ADD (COUPON_AMOUNT NUMBER(12));

COMMENT ON COLUMN TRAVEL_TICKET_ORDER.CASH_AMOUNT IS '支付中心回调回传的实付现金金额（分）；原样落库，不做与总额的匹配校验';
COMMENT ON COLUMN TRAVEL_TICKET_ORDER.COUPON_AMOUNT IS '支付中心回调回传的优惠金额（分）；原样落库，不做与总额的匹配校验';
