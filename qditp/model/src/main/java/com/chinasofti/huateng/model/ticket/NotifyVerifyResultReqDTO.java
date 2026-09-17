package com.chinasofti.huateng.model.ticket;

/**
 * IF1A-01 闸机检票通知请求业务参数。
 */
public class NotifyVerifyResultReqDTO {

    private String deviceId;
    private String itpUserId;
    private String trxType;
    private String issueChannelCode;
    private String signChannelCode;
    private String cardId;
    private String cardType;
    private String handleDateTime;
    private String handleStationCode;
    private String trxAmount;
    private String overtimeAmount;
    private String lastTicketStatus;
    private String handleResultCode;
    private String lastHandleStationCode;
    private String lastHandleDateTime;
    private String ticketTransSeq;
    /**
     * 补站类型：空=真实检票，01=补进站，02=补出站。
     */
    private String excessFareType;
    /**
     * BOM 操作类型：默认（空）= 真实闸机检票/补站，018=补进站，005=免费更新，006=付费更新。
     */
    private String adviceOpt;
    /**
     * 预留字段1；HCE 卡交易时为闸机处理后的 64 字节 HCE 卡数据。
     */
    private String reserve1;
    private String reserve2;
    /**
     * 同行票标识：Y=同行票，C=第三方票（目前均对应卡类型 0441），为空表示普通票。
     */
    private String companionFlag;
    /** 支付渠道编码（如 ALIPAY、WECHAT），来自 USER_ITP_REG_INFO.CHANNEL。 */
    private String paymentVendor;
    /** 签约流水号，来自 USER_ITP_REG_INFO.REQ_CONTRACT_NO。 */
    private String requestSignSeq;
    /** 交易渠道类型：00闸机、01蓝牙、02BOM、03自助补站。 */
    private String channelType;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
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

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getHandleStationCode() {
        return handleStationCode;
    }

    public void setHandleStationCode(String handleStationCode) {
        this.handleStationCode = handleStationCode;
    }

    public String getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(String trxAmount) {
        this.trxAmount = trxAmount;
    }

    public String getOvertimeAmount() {
        return overtimeAmount;
    }

    public void setOvertimeAmount(String overtimeAmount) {
        this.overtimeAmount = overtimeAmount;
    }

    public String getLastTicketStatus() {
        return lastTicketStatus;
    }

    public void setLastTicketStatus(String lastTicketStatus) {
        this.lastTicketStatus = lastTicketStatus;
    }

    public String getHandleResultCode() {
        return handleResultCode;
    }

    public void setHandleResultCode(String handleResultCode) {
        this.handleResultCode = handleResultCode;
    }

    public String getLastHandleStationCode() {
        return lastHandleStationCode;
    }

    public void setLastHandleStationCode(String lastHandleStationCode) {
        this.lastHandleStationCode = lastHandleStationCode;
    }

    public String getLastHandleDateTime() {
        return lastHandleDateTime;
    }

    public void setLastHandleDateTime(String lastHandleDateTime) {
        this.lastHandleDateTime = lastHandleDateTime;
    }

    public String getTicketTransSeq() {
        return ticketTransSeq;
    }

    public void setTicketTransSeq(String ticketTransSeq) {
        this.ticketTransSeq = ticketTransSeq;
    }

    /**
     * 兼容规范样例中的字段拼写 tikcetTransSeq。
     */
    public void setTikcetTransSeq(String tikcetTransSeq) {
        this.ticketTransSeq = tikcetTransSeq;
    }

    public String getExcessFareType() {
        return excessFareType;
    }

    public void setExcessFareType(String excessFareType) {
        this.excessFareType = excessFareType;
    }

    public String getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(String adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    public String getReserve1() {
        return reserve1;
    }

    public void setReserve1(String reserve1) {
        this.reserve1 = reserve1;
    }

    public String getReserve2() {
        return reserve2;
    }

    public void setReserve2(String reserve2) {
        this.reserve2 = reserve2;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    @Override
    public String toString() {
        return "NotifyVerifyResultReqDTO{" +
                "deviceId='" + deviceId + '\'' +
                ", itpUserId='" + itpUserId + '\'' +
                ", trxType='" + trxType + '\'' +
                ", issueChannelCode='" + issueChannelCode + '\'' +
                ", signChannelCode='" + signChannelCode + '\'' +
                ", cardId='" + cardId + '\'' +
                ", cardType='" + cardType + '\'' +
                ", handleDateTime='" + handleDateTime + '\'' +
                ", handleStationCode='" + handleStationCode + '\'' +
                ", trxAmount='" + trxAmount + '\'' +
                ", overtimeAmount='" + overtimeAmount + '\'' +
                ", lastTicketStatus='" + lastTicketStatus + '\'' +
                ", handleResultCode='" + handleResultCode + '\'' +
                ", lastHandleStationCode='" + lastHandleStationCode + '\'' +
                ", lastHandleDateTime='" + lastHandleDateTime + '\'' +
                ", ticketTransSeq='" + ticketTransSeq + '\'' +
                ", excessFareType='" + excessFareType + '\'' +
                ", adviceOpt='" + adviceOpt + '\'' +
                ", reserve1='" + (reserve1 != null && reserve1.length() > 8 ? reserve1.substring(0, 8) + "..." : reserve1) + '\'' +
                ", reserve2='" + reserve2 + '\'' +
                ", companionFlag='" + companionFlag + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                '}';
    }
}
