-- ALIPAY_PAY_TXN_DETAIL 新增 TRANS_TIME 列：承载支付中心回调报文里的 transTime（支付时刻）。
--
-- 【为什么加回来】本表重建时（alipay-pay-txn-detail-rebuild-migration.sql 第二轮）把对端回传的
-- transAmount / transTime / transStatus / cardNo 四个字段整批删掉了，理由是「当前态不需要报文原文」。
-- 但支付宝出行记录查询的应答契约里有一个 payOrderNoDate（支付订单日期），旧实现取的正是
-- ALIPAY_PAY_LOG.TRANS_TIME —— 也就是同一个 transTime。行程列表迁到「GATE_TXN_PAY 过滤分页 +
-- 按 ORDER_NO 批量取本表明细」之后，这个值必须能从本表拿到，否则只有两条退路：
--   a) 用 GATE_TXN_PAY.OUT_TIME 顶替 —— 语义从「支付时刻」退化成「出站时刻」；
--   b) 批量查询再 join ALIPAY_PAY_CALLBACK_LOG —— 那张表一单多行（重推），取哪一行没有确定答案。
-- 因此按 2026-09-18 决策，把 transTime 收进本表当前态。**这不是把那四个字段都加回来**：
-- transAmount / transStatus / cardNo 仍然 NEVER 加回（金额权威是 AMOUNT，状态权威是 PAY_STATUS
-- 与 GATE_TXN_PAY.DEBIT_STATUS，卡号在主表），本次只加 TRANS_TIME 这一列。
--
-- 【已被后续脚本取代的部分，读到这里 MUST 接着看 alipay-pay-txn-detail-trans-time-normalize-migration.sql】
-- 下面「原文直存不解析」与本文件末尾那条 COMMENT ON 是 2026-09-18 的口径，2026-09-20 已按用户裁决翻转：
-- 该列现在统一存 14 位 yyyyMMddHHmmss（归一在 alipay-pay-sign-server 1.1.43 的
-- PayTxnCallbackWriter.normalizeTransTime 做，存量行与列注释由那个 normalize 脚本改掉）。
-- 本文件保留原样不改 SQL —— 它记录的是「这一列当初为什么加、为什么是 VARCHAR2」，属历史依据；
-- NEVER 据本文件的注释认为该列仍是报文原文。
--
-- 【类型与格式】VARCHAR2(32 CHAR)，与 ALIPAY_PAY_CALLBACK_LOG.TRANS_TIME 同宽。
-- 原文直存不解析：存量与在途报文格式并不统一，实测既有 '2026-09-18 15:49:30' 也有 '20260918021500'
-- 两种写法（见 alipay-pay-txn-schema.sql 关于旧表 TRANS_TIME 的说明）。因此 NEVER 建成 DATE /
-- TIMESTAMP，也 NEVER 在查询里 TO_DATE(TRANS_TIME, ...) —— 那既走不到索引，遇到另一种格式还会
-- 抛 ORA-01861。要按时间范围筛选一律去主表用 GATE_TXN_PAY.TXN_DATE / OUT_TIME。
--
-- 【写入方】只有支付回调一处：AlipayPayTxnDetailMapper.updatePayCallback 的 SET 段，套 NVL 不覆盖
-- 已有值（与 CHANNEL_ORDER_NO 同款理由：支付中心重推报文字段稀疏，不套 NVL 会被后续重推抹成 NULL）。
-- insert 时恒为 NULL —— 扣费申请阶段对端还没告诉我们支付时刻。payQuery 方向不写本列，与
-- ADR-D136 决策三「payQuery 零落库」一致。
--
-- 【不建索引】本列只作为出参回填，从不做查询谓词，加索引纯属浪费写入代价。

ALTER TABLE ALIPAY_PAY_TXN_DETAIL ADD (TRANS_TIME VARCHAR2(32 CHAR));

COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.TRANS_TIME IS '支付时刻，支付中心回调报文transTime原文直存不解析；格式不统一（既有yyyy-MM-dd HH:mm:ss也有yyyyMMddHH24MISS），NEVER建成DATE也NEVER用TO_DATE查询；支付宝出行记录应答的payOrderNoDate取此列；只由支付回调写入且套NVL';
