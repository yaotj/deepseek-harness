-- blacklist-server 解黑方向渠道同步 outbox 载体列
--
-- 背景：解除黑名单时整行被搬到 BLACKLIST_RELEASED、主表行随即删除，因此主表
-- BLACKLIST.CHANNEL_SYNC_* 那四列承载不了「解黑通知推没推成功」——通知要发的那一刻，
-- 载体行已经不在主表了。缺这四列时，解黑通知一旦推失败即永久丢失，后果是
-- 我方已解黑、支付宝渠道侧仍按拉黑处理，该卡照样过不了闸，且无人推进、无告警。
--
-- 四列形状与 BLACKLIST 上的同名列逐字一致（16 / TIMESTAMP(6) / NUMBER(4) / 500）。
-- NEVER 让两张表的同语义列分叉：扫表补偿与状态判定在两侧共用同一套取值
-- （PENDING / SUCCESS / FAILED / 终态），列长不一致会在截断处静默丢信息。
--
-- 历史行 CHANNEL_SYNC_STATUS 保持 NULL，代表「本次改造之前解除的、不参与补偿」。
-- 扫表 SQL MUST 用 IN ('PENDING','FAILED') 白名单，NEVER 写成「非 SUCCESS 即扫」——
-- 后者会把这些 NULL 历史行全部捞进来重推一遍。
--
-- 执行记录：2026-09-18 在 AFCITPDB（172.20.222.3:1521，用户 qditp）执行并用
-- USER_TAB_COLS.CHAR_LENGTH + USER_INDEXES / USER_IND_COLUMNS 回查通过。

ALTER TABLE BLACKLIST_RELEASED ADD (
    CHANNEL_SYNC_STATUS VARCHAR2(16),
    CHANNEL_SYNC_TIME TIMESTAMP(6),
    CHANNEL_SYNC_RETRY NUMBER(4),
    CHANNEL_SYNC_FAIL_REASON VARCHAR2(500)
);

CREATE INDEX IDX_BL_RELEASED_SYNC_SCAN ON BLACKLIST_RELEASED (CHANNEL_SYNC_STATUS, CHANNEL_SYNC_TIME);
