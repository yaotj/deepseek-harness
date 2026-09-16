-- 为 APP_TERMINATION_REQUEST 增加「解约成功 → 账户域清理支付通道」的可靠投递状态列。
-- 背景：PaySignWorkflow.receiveTerminationResult 目前在 @Transactional 内调
-- removeAccountPayChannel（事务内出网，违反 AGENTS.md 5.2），失败即抛
-- TerminationException 回滚，靠支付平台重推自愈。
-- 列名与同表的 NOTIFY_* 一组对称，语义也照抄：状态 + 重试次数 + 时间 + 结果。
-- 详见 docs/domain/decisions.md ADR-D8 第一处。
--
-- NEVER 只执行本脚本就改 Java：本组列是「拆事务边界 + 补偿端点 + 工单」三件事的前置，
-- 单独把 RPC 移出事务反而比现状更糟（本地提交后 TERMINATION_STATUS=SUCCESS，
-- 上游重推在幂等短路处返回成功，通道永远删不掉）。执行顺序见 ADR-D8。
ALTER TABLE APP_TERMINATION_REQUEST ADD (
    CHANNEL_SYNC_STATUS      VARCHAR2(32 CHAR),
    CHANNEL_SYNC_RETRY_COUNT NUMBER(22) DEFAULT 0,
    CHANNEL_SYNC_TIME        TIMESTAMP(6),
    CHANNEL_SYNC_RESULT      VARCHAR2(1024 CHAR)
);

COMMENT ON COLUMN APP_TERMINATION_REQUEST.CHANNEL_SYNC_STATUS IS '向账户域清理支付通道的投递状态：PENDING待投递、SUCCESS已清理、FAILED待重试；NULL表示本行早于改造，NEVER被补偿扫描捞取';
COMMENT ON COLUMN APP_TERMINATION_REQUEST.CHANNEL_SYNC_RETRY_COUNT IS '清理重试次数，达上限后不再扫描、转人工处理';
COMMENT ON COLUMN APP_TERMINATION_REQUEST.CHANNEL_SYNC_TIME IS '最近一次清理投递时间，配合滞留判定';
COMMENT ON COLUMN APP_TERMINATION_REQUEST.CHANNEL_SYNC_RESULT IS '最近一次清理的返回码与消息，超长由调用方截断';

CREATE INDEX IDX_ATR_CHANNEL_SYNC
    ON APP_TERMINATION_REQUEST (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_RETRY_COUNT);
