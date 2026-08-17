-- ALIPAY_PAY_LOG 增加 invoice 字段
-- 对应字段：发票状态筛选
-- 适用模块：alipay-pay-sign-server

ALTER TABLE ALIPAY_PAY_LOG
    ADD INVOICE VARCHAR2(32 CHAR);

COMMENT ON COLUMN ALIPAY_PAY_LOG.INVOICE IS '发票状态';
