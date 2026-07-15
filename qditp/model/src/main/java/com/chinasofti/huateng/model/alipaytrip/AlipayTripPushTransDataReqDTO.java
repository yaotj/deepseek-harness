package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 支付宝出行-行程数据推送请求参数。
 * 与APP行程数据通知参数相同。
 */
public class AlipayTripPushTransDataReqDTO {

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * 本次交易类型  "01"：进站  "02"：出站  "03"：超时出站
     */
    private String trxType;

    /**
     * 车票状态  "02"：结束行程  "03"：SJT发售  "04"：已进站  "05"：已出站  "06"：超时出站  "08"：20分钟内免费更新  "09"：20分钟外付费更新  "10"：入站码更新  "80"：自助补出站  "81"：自助补进站
     */
    private String ticketStatus;

    /**
     * 支付渠道编码
     */
    private String signChannelCode;

    /**
     * 逻辑卡号
     */
    private String cardId;

    /**
     * 卡类型  "0441"：二维码后付费单程票  "0442"：HCE 后付费单程票  "0443"：新版 HCE 后付费单程票  "0444"：员工票  "0445"：一日票  "0446"：三日票  "0447"：七日票  "0448"：月票  "044A"：爱山东
     */
    private String cardType;

    /**
     * 处理时间  yyyyMMddHHmmss
     */
    private String handleDateTime;

    /**
     * 站点编码
     */
    private String handleStationCode;

    /**
     * 本次交易金额  以“分”为单位的整数字符串
     */
    private String trxAmount;

    /**
     * 超时附加金额  以“分”为单位的整数字符串
     */
    private String overtimeAmount;

    /**
     * 上一次票卡状态  "02"：结束行程  "03"：SJT发售  "04"：已进站  "05"：已出站  "06"：超时出站  "08"：20分钟内免费更新  "09"：20分钟外付费更新  "10"：入站码更新  "80"：自助补出站  "81"：自助补进站
     */
    private String lastTicketStatus;

    /**
     * 上一次处理车站编码
     */
    private String lastHandleStationCode;

    /**
     * 上一次处理时间  yyyyMMddHHmmss
     */
    private String lastHandleDateTime;

    /**
     * 票卡交易序列号
     */
    private String tikcetTransSeq;

    /**
     * 设备编号
     */
    private String deviceId;

    /**
     * 渠道类型  "00"：闸机  "01"：蓝牙  "02"：BOM  "03"：自助补站
     */
    private String channelType;

    /**
     * 同行票是Y，第三方是C，目前两种标识对应的卡类型都是0441 非必填
     */
    private String companionFlag;

    /**
     * 日票标识
     */
    private String countingFlag;

    /**
     * 行程数据JSON
     */
    private String transData;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
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

    public String getTikcetTransSeq() {
        return tikcetTransSeq;
    }

    public void setTikcetTransSeq(String tikcetTransSeq) {
        this.tikcetTransSeq = tikcetTransSeq;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getCountingFlag() {
        return countingFlag;
    }

    public void setCountingFlag(String countingFlag) {
        this.countingFlag = countingFlag;
    }

    public String getTransData() {
        return transData;
    }

    public void setTransData(String transData) {
        this.transData = transData;
    }
}
