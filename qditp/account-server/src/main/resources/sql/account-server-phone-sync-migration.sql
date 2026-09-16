-- 为 USER_PHONE_CHANGE_LOG 增加「手机号变更事实 → 支付域」的可靠投递状态列。
-- 背景：AccountApplicationServiceImpl.updatePhone 目前在 @Transactional 内调
-- paySignClient.updatePaySignDisplayAccount（事务内出网，违反 AGENTS.md 5.2）。
-- 改造后该 RPC 移到事务外，成败落到本组列，由 @Scheduled 扫表补偿重推，
-- 达重试上限转异常工单。列名与 APP_TERMINATION_REQUEST 的 NOTIFY_* 对称。
-- 详见 docs/domain/decisions.md ADR-D8。
ALTER TABLE USER_PHONE_CHANGE_LOG ADD (
    SIGN_SYNC_STATUS      VARCHAR2(32 CHAR),
    SIGN_SYNC_RETRY_COUNT NUMBER(22) DEFAULT 0,
    SIGN_SYNC_TIME        TIMESTAMP(6),
    SIGN_SYNC_RESULT      VARCHAR2(1024 CHAR)
);

COMMENT ON COLUMN USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS IS '向支付域同步显示账号的投递状态：PENDING-待投递，SUCCESS-已送达，FAILED-投递失败待重试；NULL 表示本行早于改造，NEVER 被补偿扫描捞取';
COMMENT ON COLUMN USER_PHONE_CHANGE_LOG.SIGN_SYNC_RETRY_COUNT IS '投递重试次数，达配置上限后不再扫描、转异常工单人工处理';
COMMENT ON COLUMN USER_PHONE_CHANGE_LOG.SIGN_SYNC_TIME IS '最近一次投递时间，配合 staleMinutes 判定 PENDING 滞留';
COMMENT ON COLUMN USER_PHONE_CHANGE_LOG.SIGN_SYNC_RESULT IS '最近一次投递的返回码与消息，超长由调用方截断';

CREATE INDEX IDX_UPCL_SIGN_SYNC
    ON USER_PHONE_CHANGE_LOG (SIGN_SYNC_STATUS, SIGN_SYNC_RETRY_COUNT);
