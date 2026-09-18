-- 支付宝渠道签约表：补「支付通道同步」outbox 四列（ADR-D129）。
-- 背景：addContract 原先在 @Transactional 内先 INSERT 本地签约行、再调 account 域
-- updatePaymentChannel（事务内出网），失败即整笔回滚。两条都违反 AGENTS.md §5.2：
--   1) @Transactional 方法内 NEVER 发起任何 RPC（行锁持有时长 = 对端响应时长）；
--   2) 顺序颠倒时留下「本地已成、远端未配」的不一致且无补偿出口。
-- 改造后形态与 pay-sign-server 的 APP_TERMINATION_REQUEST.CHANNEL_SYNC_* 同款（ADR-D48）：
-- 签约行落库即带 PENDING，提交后（事务外）出网，按结果回写这四列；未成功的留给补偿扫描。
--
-- 本脚本对已存在的库有效，schema 文件不承担该职责（AGENTS.md §8 那条「*-schema.sql
-- 只服务新建库」）。执行后 MUST 用 USER_TAB_COLS 回查并把结果写进 ADR-D129。
ALTER TABLE ALIPAY_SIGN_INFO ADD (
    CHANNEL_SYNC_STATUS       VARCHAR2(16)  DEFAULT 'PENDING' NOT NULL,
    CHANNEL_SYNC_RETRY_COUNT  NUMBER(10)    DEFAULT 0 NOT NULL,
    CHANNEL_SYNC_TIME         DATE,
    CHANNEL_SYNC_RESULT       VARCHAR2(500)
);

COMMENT ON COLUMN ALIPAY_SIGN_INFO.CHANNEL_SYNC_STATUS IS '支付通道同步状态：PENDING待同步、SUCCESS已同步、FAILED业务拒绝需人工；NEVER 用它表达签约状态，签约状态在 SIGN_STATUS';
COMMENT ON COLUMN ALIPAY_SIGN_INFO.CHANNEL_SYNC_RETRY_COUNT IS '支付通道同步已尝试次数，每次回写 +1，供补偿扫描做退避与放弃判定';
COMMENT ON COLUMN ALIPAY_SIGN_INFO.CHANNEL_SYNC_TIME IS '最近一次支付通道同步的尝试时刻';
COMMENT ON COLUMN ALIPAY_SIGN_INFO.CHANNEL_SYNC_RESULT IS '最近一次同步的结果说明，失败原因原文；只用于人工排查，NEVER 作为分支判据';

-- 补偿扫描按「状态 + 时间」取批，索引与 pay-sign 侧同形。
CREATE INDEX IDX_ASI_CHANNEL_SYNC ON ALIPAY_SIGN_INFO (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_TIME);
