package com.chinasofti.huateng.collectticket.entity;

import java.time.LocalDateTime;

/**
 * Ticket_Collect_Logs 铁运维保取票记录表实体。
 * 订单号与 Ticket_Collect_Info 一一对应。
 */
public class TicketCollectLogs {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 进站站点编码。
     */
    private String entryStationCode;

    /**
     * 目的站点编码。
     */
    private String exitStationCode;

    /**
     * 票价（单位：分）。
     */
    private Integer ticketPrice;

    /**
     * 单程票数量。
     */
    private Integer singelTicketNum;

    /**
     * 单程票类型：0-非本站进本站出。
     */
    private Integer singleTicketType;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 订单状态：0-未支付，100-支付成功，1-99-支付失败或异常。
     */
    private Integer orderStatus;

    /**
     * 创建订单时间。
     */
    private LocalDateTime createTms;

    /**
     * 取票时间。
     */
    private LocalDateTime collectTms;

    /**
     * 取票状态：0-未取票（待支付或已支付待处理），100-取票成功，1-99-取票失败，110-重复取票成功。
     */
    private Integer collectStatus;

    /**
     * 取票设备编号。
     */
    private String deviceId;

    /**
     * 设备取票二维码生成时间。
     */
    private LocalDateTime qrcodeGenDate;

    /**
     * 设备随机因子。
     */
    private String randomFact;

    /**
     * 实际出票数量。
     */
    private Integer actualTakeTicketNum;

    /**
     * 故障凭证号。
     */
    private String faultSlipSeq;

    /**
     * 错误代码：2101-取票二维码超时等。
     */
    private String errorCode;

    /**
     * 错误详细信息。
     */
    private String errorMessage;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(Integer singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public Integer getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(Integer singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getCollectTms() {
        return collectTms;
    }

    public void setCollectTms(LocalDateTime collectTms) {
        this.collectTms = collectTms;
    }

    public Integer getCollectStatus() {
        return collectStatus;
    }

    public void setCollectStatus(Integer collectStatus) {
        this.collectStatus = collectStatus;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(LocalDateTime qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public String getFaultSlipSeq() {
        return faultSlipSeq;
    }

    public void setFaultSlipSeq(String faultSlipSeq) {
        this.faultSlipSeq = faultSlipSeq;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "TicketCollectLogs{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice=" + ticketPrice +
                ", singelTicketNum=" + singelTicketNum +
                ", singleTicketType=" + singleTicketType +
                ", channelCode='" + channelCode + '\'' +
                ", orderStatus=" + orderStatus +
                ", collectStatus=" + collectStatus +
                ", deviceId='" + deviceId + '\'' +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
