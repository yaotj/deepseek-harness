-- SUPPLEMENT_ORDER 补 outbox 四列：把「补款单已在本地落库、还要登记到 collect-pay 的 APP 订单表」
-- 这件事变成可补偿的持久事实。2026-09-14 随「APP 订单表写入收口到 owner」改造一并新增。
--
-- 为什么需要它：收口前，两张 collect-pay 的表是由本模块在同一个本地事务里直写的，
-- 因此「补款单存在」与「乘客能付款」天然同生同死。改成 RPC 后这个保证消失了，
-- 而两种失败方向的代价并不对称：
--   先调远端后落本地（AGENTS.md §5.2 通则） -> 远端成功、本地失败时，
--     collect-pay 里留下一行 PAY_STATUS='0' 的可支付孤儿订单，乘客付得进去、
--     我方却没有补款单去收敛这笔钱 —— 资损方向。
--   先落本地后调远端（本改造采用）       -> 本地成功、远端失败时，
--     乘客暂时付不了款，扫表补偿重推即可自愈，无资损。
-- 因此本链路 **有意偏离 §5.2 的「先远端后本地」通则**，改用 outbox。裁决人：用户 2026-09-14。
--
-- 列的形状、状态取值与扫表 SQL 的四个坑全部照 docs/domain/outbox.md 的模板，
-- NEVER 另起一套列名或状态值。前缀 SALE_SYNC_ 表示投递目标是销售域（collect-pay-server）。

ALTER TABLE SUPPLEMENT_ORDER ADD (
    SALE_SYNC_STATUS      VARCHAR2(32 CHAR),
    SALE_SYNC_RETRY_COUNT NUMBER DEFAULT 0,
    SALE_SYNC_TIME        TIMESTAMP(6),
    SALE_SYNC_RESULT      VARCHAR2(1024 CHAR)
);

-- 扫表谓词是 SALE_SYNC_STATUS IN ('PENDING','FAILED')，因此按状态 + 时间建索引。
-- 历史行该列为 NULL、天然扫不到，这是 outbox 模板要求的行为：NEVER 把 NULL 兜底成 PENDING，
-- 否则本功能上线那一刻会把改造前的全部补款单一次性推给 collect-pay。
CREATE INDEX IDX_SUPPLEMENT_ORDER_SALESYNC ON SUPPLEMENT_ORDER (SALE_SYNC_STATUS, CREATE_TIME);
