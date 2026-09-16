-- GATE_TXN_PAY 新增 INDUSTRY_DETAIL 列：承载支付宝出行（ISSUE_CHANNEL_CODE='07'）的行业明细 JSON。
--
-- 为什么必须落库、不能在 gate-txn-pay-server 现算：
-- 该 JSON 共 21 键，其中 entryLineCode / entryLineName / exitLineCode / exitLineName /
-- entryDeviceCode / entryId / exitId / cardNum / cardIssueCode 这 9 项 GATE_TXN_PAY 本身没有列，
-- 它们由 fep-dev-server 在出站当次通过三路并行 RPC 组装（para 单查站线、ticket 查进站设备、
-- 按 itpUserId+时间戳算 entryId/exitId）。出站是唯一能拿到这些值的时点，
-- 因此 MUST 在落单时整块存下来，供首次扣费与后续重试复用。
--
-- 长度取 VARCHAR2(4000 CHAR)：实测键名约 250 字符 + 值以 ASCII 为主，
-- 仅站名/线路名是中文（4 项 × 约 6 字 × 3 字节），典型报文约 800~1000 字节，余量充足。
-- NEVER 改成 CLOB：Oracle 的 COUNT(<CLOB 列>) 非法，且经 mcp_database_qd 查询时
-- 会报成通用的 cast 错误、看不出真实成因（AGENTS.md §8）。
--
-- 注意：GATE_TXN_PAY 的建表脚本原先放错模块（在 fep-dev-server 下），2026-09-14 已迁到本模块
-- （gate-txn-pay-server/src/main/resources/sql/gate-txn-pay-schema.sql），与「表的 owner 模块」一致。
-- 两处都要改：schema.sql 服务新建库，本脚本服务已存在的库（AGENTS.md §8）。
--
-- 分区表加可空列是纯元数据操作，不重建分区、不锁数据。

ALTER TABLE GATE_TXN_PAY ADD (INDUSTRY_DETAIL VARCHAR2(4000 CHAR));

COMMENT ON COLUMN GATE_TXN_PAY.INDUSTRY_DETAIL IS '支付宝出行行业明细JSON，21键，出站落单时由fep-dev-server组装后整块透传，仅ISSUE_CHANNEL_CODE=07有值';

-- 回查（AGENTS.md §8 要求 (b)，执行后 MUST 跑这条并把结果写进 ADR）：
-- SELECT COLUMN_NAME, DATA_TYPE, CHAR_LENGTH, CHAR_USED, NULLABLE
--   FROM USER_TAB_COLS
--  WHERE TABLE_NAME = 'GATE_TXN_PAY' AND COLUMN_NAME = 'INDUSTRY_DETAIL';
-- 期望：VARCHAR2 / CHAR_LENGTH=4000 / CHAR_USED='C' / NULLABLE='Y'
