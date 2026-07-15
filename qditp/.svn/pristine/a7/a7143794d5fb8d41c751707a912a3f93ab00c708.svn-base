package com.chinasofti.huateng.online.entity;

import java.time.LocalDateTime;

public class QRCodeStatus {
    /**
     * 二维码票卡状态快照实体。
     * 对应二维码票/HCE 票在 ITP 侧的最新状态，用于支撑：
     * 1. BOM 票卡分析与更新
     * 2. AGM 检票通知与状态查询
     * 3. 行业数据/HCE 数据回写
     */
    private String cardId;
    /** 逻辑卡号，票卡状态表主键。 */
    private String itpUserId;
    /** ITP 用户编码。 */
    private String providerId;
    /** 发行方代码。 */
    private String msisdn;
    /** 手机号，规范中 BOM 票卡分析预留字段。 */
    private String cardType;
    /** 卡类型，如 0441 二维码后付费单程票。 */
    private String cardStatus;
    /** 最新票卡状态码，如 03 发售、04 已进站、05 已出站。 */
    private String issueChannelCode;
    /** 发行渠道编码。 */
    private String signChannelCode;
    /** 签约渠道编码/支付渠道编码。 */
    private String lastLineCode;
    /** 上次处理线路编码。 */
    private String lastStationCode;
    /** 上次处理车站编码。 */
    private LocalDateTime lastHandleDateTime;
    /** 上次交易/处理时间。 */
    private Integer lastTransAmount;
    /** 上次交易金额。 */
    private String lastTicketTransSeq;
    /** 上次交易序列号。 */
    private String industryData;
    /** 平台侧最新行业数据快照。 */
    private String hceData;
    /** HCE 数据块。 */
    private String queryLockedYn;
    /** 是否被平台查询锁定，Y-锁定，N-未锁定。 */
    private String lastDeviceId;
    /** 最后一次处理该票卡的设备编号。 */
    private LocalDateTime createTms;
    /** 创建时间。 */
    private LocalDateTime updateTms;
    /** 最后更新时间。 */

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }

    public String getIssueChannelCode() {
        return issueChannelCode;
    }

    public void setIssueChannelCode(String issueChannelCode) {
        this.issueChannelCode = issueChannelCode;
    }

    public String getSignChannelCode() {
        return signChannelCode;
    }

    public void setSignChannelCode(String signChannelCode) {
        this.signChannelCode = signChannelCode;
    }

    public String getLastLineCode() {
        return lastLineCode;
    }

    public void setLastLineCode(String lastLineCode) {
        this.lastLineCode = lastLineCode;
    }

    public String getLastStationCode() {
        return lastStationCode;
    }

    public void setLastStationCode(String lastStationCode) {
        this.lastStationCode = lastStationCode;
    }

    public LocalDateTime getLastHandleDateTime() {
        return lastHandleDateTime;
    }

    public void setLastHandleDateTime(LocalDateTime lastHandleDateTime) {
        this.lastHandleDateTime = lastHandleDateTime;
    }

    public Integer getLastTransAmount() {
        return lastTransAmount;
    }

    public void setLastTransAmount(Integer lastTransAmount) {
        this.lastTransAmount = lastTransAmount;
    }

    public String getLastTicketTransSeq() {
        return lastTicketTransSeq;
    }

    public void setLastTicketTransSeq(String lastTicketTransSeq) {
        this.lastTicketTransSeq = lastTicketTransSeq;
    }

    public String getIndustryData() {
        return industryData;
    }

    public void setIndustryData(String industryData) {
        this.industryData = industryData;
    }

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    public String getQueryLockedYn() {
        return queryLockedYn;
    }

    public void setQueryLockedYn(String queryLockedYn) {
        this.queryLockedYn = queryLockedYn;
    }

    public String getLastDeviceId() {
        return lastDeviceId;
    }

    public void setLastDeviceId(String lastDeviceId) {
        this.lastDeviceId = lastDeviceId;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
