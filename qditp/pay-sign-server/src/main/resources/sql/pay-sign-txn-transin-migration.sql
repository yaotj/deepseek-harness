-- 为 PAY_TXN_DETAIL 表添加 TRANS_IN 字段，用于保存支付平台返回的入账账户/商户号
-- 关联修改：PayTxnDetail.java、PayTxnDetailMapper.xml、PaySignWorkflow.java

-- 添加列（允许为空，历史数据无需回刷）
ALTER TABLE PAY_TXN_DETAIL ADD TRANS_IN VARCHAR2(64 CHAR);

-- 列注释
COMMENT ON COLUMN PAY_TXN_DETAIL.TRANS_IN IS '入账账户/商户号（支付平台 data.transIn）';
