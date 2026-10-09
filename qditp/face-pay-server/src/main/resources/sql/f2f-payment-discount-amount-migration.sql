-- F2F_PAYMENT 支付回调优惠字段落库迁移（2026-09-20）
-- 背景：支付中心支付结果回调（契约 §5.1）带 cashAmount / couponAmount，PayNoticeReqDTO 一直接得到，
--       但 F2F_PAYMENT 没有对应列、markSuccess 也没写，两个金额只能丢掉。
-- 口径：原样照写，NEVER 加「cash + coupon 是否等于订单金额」这类校验（支付中心实测三值恒相等、属无效值）。
--       回写走 updateCallbackAmounts 的 NVL 保护，主动查询与失败分支不会把已落的值抹成 NULL。
-- 执行环境：AFCITPDB（172.20.222.3:1521，用户 qditp）

ALTER TABLE F2F_PAYMENT ADD (CASH_AMOUNT NUMBER(12));
ALTER TABLE F2F_PAYMENT ADD (COUPON_AMOUNT NUMBER(12));

COMMENT ON COLUMN F2F_PAYMENT.CASH_AMOUNT IS '支付中心回调回传的实付现金金额（分）；原样落库，不做与订单金额的匹配校验';
COMMENT ON COLUMN F2F_PAYMENT.COUPON_AMOUNT IS '支付中心回调回传的优惠金额（分）；原样落库，不做与订单金额的匹配校验';
