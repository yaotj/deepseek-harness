package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

/**
 * 黑名单解除历史表实体，保存被解除记录的完整快照。
 *
 * <p>同一张卡可反复拉黑解除，因此本表 CARD_ID 只有普通索引、NEVER 建唯一约束。</p>
 */
public class BlacklistReleased {
    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 解除前在 BLACKLIST 中的主键ID。
     */
    private Long originId;

    /**
     * 卡ID。
     */
    private String cardId;

    /**
     * 三方用户ID。
     */
    private String thirdUserId;

    /**
     * 卡类型编码。
     */
    private String cardType;

    /**
     * 业务渠道，取值同 BLACKLIST.CHANNEL_CODE。
     */
    private String channelCode;

    /**
     * 发起方，取值同 BLACKLIST.BLACK_SOURCE。
     */
    private String blackSource;

    /**
     * 拉黑原因，取值同 BLACKLIST.BLACK_CAUSE。
     */
    private String blackCause;

    /**
     * 关联业务单号。
     */
    private String bizNo;

    /**
     * 原拉黑备注。
     */
    private String reason;

    /**
     * 原拉黑操作者。
     */
    private String createBy;

    /**
     * 原拉黑时间。
     */
    private LocalDateTime createTime;

    /**
     * 解除时间。
     */
    private LocalDateTime releaseTime;

    /**
     * 解除原因，NEVER 复制原拉黑原因。
     */
    private String releaseReason;

    /**
     * 解除操作者。
     */
    private String releaseBy;

    /**
     * 渠道同步状态：PENDING 待推 / SUCCESS 已推达 / FAILED 待补偿 / REJECTED 被业务拒绝（终态）。
     *
     * <p>解黑通知的载体在本表、不在主表：解除时主表那行已被删除，BLACKLIST.CHANNEL_SYNC_* 承载不了
     * 「解黑通知推没推成功」。历史行为 NULL，代表改造前解除的、不参与补偿，扫表 SQL MUST 用
     * IN ('PENDING','FAILED') 白名单把它们排除在外。
     */
    private String channelSyncStatus;

    /**
     * 渠道同步收口时间，成功与失败都写。
     */
    private LocalDateTime channelSyncTime;

    /**
     * 渠道同步已重试次数，只在 FAILED 分支 +1。
     */
    private Integer channelSyncRetry;

    /**
     * 渠道同步失败原因，落库前 MUST 截断到列长 500。
     */
    private String channelSyncFailReason;

    public String getChannelSyncStatus() {
        return channelSyncStatus;
    }

    public void setChannelSyncStatus(String channelSyncStatus) {
        this.channelSyncStatus = channelSyncStatus;
    }

    public LocalDateTime getChannelSyncTime() {
        return channelSyncTime;
    }

    public void setChannelSyncTime(LocalDateTime channelSyncTime) {
        this.channelSyncTime = channelSyncTime;
    }

    public Integer getChannelSyncRetry() {
        return channelSyncRetry;
    }

    public void setChannelSyncRetry(Integer channelSyncRetry) {
        this.channelSyncRetry = channelSyncRetry;
    }

    public String getChannelSyncFailReason() {
        return channelSyncFailReason;
    }

    public void setChannelSyncFailReason(String channelSyncFailReason) {
        this.channelSyncFailReason = channelSyncFailReason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginId() {
        return originId;
    }

    public void setOriginId(Long originId) {
        this.originId = originId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getBlackSource() {
        return blackSource;
    }

    public void setBlackSource(String blackSource) {
        this.blackSource = blackSource;
    }

    public String getBlackCause() {
        return blackCause;
    }

    public void setBlackCause(String blackCause) {
        this.blackCause = blackCause;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getReleaseTime() {
        return releaseTime;
    }

    public void setReleaseTime(LocalDateTime releaseTime) {
        this.releaseTime = releaseTime;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public String getReleaseBy() {
        return releaseBy;
    }

    public void setReleaseBy(String releaseBy) {
        this.releaseBy = releaseBy;
    }
}
