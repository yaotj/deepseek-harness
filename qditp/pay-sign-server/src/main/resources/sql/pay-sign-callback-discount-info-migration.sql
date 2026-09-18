-- 为 PAY_CALLBACK_LOG 表添加 DISCOUNT_INFO 字段，保存支付中心回调的渠道优惠详情原文（网关文档 V1.2 新增 discountInfo）
-- 关联修改：PayCallbackLog.java、PayCallbackLogMapper.xml、PayTxnRules.buildPayCallbackLog、model/app/ReceivePayResultReqDTO.java
--
-- 为什么落这张表而不是 PAY_TXN_DETAIL：
--   渠道优惠是「回调事实」，随重推各存一份；而 PAY_TXN_DETAIL.DISCOUNT_INFO 已被闸机侧自算优惠占用
--   （由 RequestPayReqDTO.discountInfo 在发起支付时写入），两个来源口径不同，混进同一列后无法区分。
--
-- 长度取 2000 而非 CLOB：优惠详情是至多数项的小数组，VARCHAR2 可直接参与 WHERE / LIKE 与索引；
--   同表 RAW_BODY 是 CLOB（存整包原文），两者分工不同。若后续实测超长再评估改 CLOB。

-- 添加列（允许为空，历史数据无需回刷 —— 2026-09-17 实测 179 条 PAY 回调原文里 0 条含该字段）
ALTER TABLE PAY_CALLBACK_LOG ADD DISCOUNT_INFO VARCHAR2(2000 CHAR);

-- 列注释
COMMENT ON COLUMN PAY_CALLBACK_LOG.DISCOUNT_INFO IS '渠道优惠详情原文 JSON 数组（支付中心回调 discountInfo，元素含 type/name/amount）';
