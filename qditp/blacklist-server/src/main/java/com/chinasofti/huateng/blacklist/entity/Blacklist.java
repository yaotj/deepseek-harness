package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;

/**
 * 黑名单表实体，只承载当前生效记录；解除后的快照见 {@link BlacklistReleased}。
 */
public class Blacklist {
    /**
     * 主键ID。
     */
    private Long id;

    /**
     * 卡ID，唯一业务键。
     */
    private String cardId;

    /**
     * 三方用户ID，审计冗余，不参与命中判定。
     */
    private String thirdUserId;

    /**
     * 卡类型编码（票种，如0441），出向通知契约需要。
     */
    private String cardType;

    /**
     * 业务渠道：01地铁APP，02支付宝，99未知。
     */
    private String channelCode;

    /**
     * 发起方：01系统自动，02渠道通知，09运维手工。
     */
    private String blackSource;

    /**
     * 拉黑原因：01未付费欠费，02挂失补卡，09其他。
     */
    private String blackCause;

    /**
     * 关联业务单号，语义由 channelCode 与 blackSource 共同决定。
     */
    private String bizNo;

    /**
     * 拉黑备注，自由文本，非判定依据。
     */
    private String reason;

    /**
     * 操作者：运维手工记管理员账号，系统触发记服务名。
     */
    private String createBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 渠道同步状态：PENDING/SUCCESS/FAILED/REJECTED。
     */
    private String channelSyncStatus;

    /**
     * 渠道同步时间。
     */
    private LocalDateTime channelSyncTime;

    /**
     * 渠道同步重试次数。
     */
    private Integer channelSyncRetry;

    /**
     * 渠道同步失败原因。
     */
    private String channelSyncFailReason;

    /**
     * 行状态：ACTIVE 生效中 / RELEASING 解除中（解除通知尚未推达渠道）。
     *
     * <p>RELEASING 的行<b>仍算黑名单</b>：判黑入口（countByCardIds / selectByCardIds / queryBlackList）
     * 刻意不带 STATUS 过滤，通知没推成功前 NEVER 提前放行。解除通知推成功后该行才被搬进
     * BLACKLIST_RELEASED 并从主表删除，因此本表里永远看不到「已解除」这个状态。
     */
    private String status;

    /**
     * 解除原因，解除中暂存在主表，推成功后随快照搬进 BLACKLIST_RELEASED。
     */
    private String releaseReason;

    /**
     * 解除操作者，与 releaseReason 同为暂存字段。
     */
    private String releaseBy;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
