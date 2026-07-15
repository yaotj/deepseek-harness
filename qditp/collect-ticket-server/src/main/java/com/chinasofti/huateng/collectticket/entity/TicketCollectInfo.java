package com.chinasofti.huateng.collectticket.entity;

import java.time.LocalDateTime;

/**
 * Ticket_Collect_Info 铁运维保取票订单信息表实体。
 * 订单号与 Ticket_Collect_Logs 一一对应。
 */
public class TicketCollectInfo {
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
     * 订单状态：0-未支付，100-支付成功，1-99-支付失败或异常（其他支付状态定义）。
     */
    private Integer orderStatus;

    /**
     * 创建订单时间。
     */
    private LocalDateTime createTms;

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
     * 取票时间。
     */
    private LocalDateTime collectTms;

    /**
     * 取票状态：0-未取票（待支付或已支付待处理），1-已计次，100-取票成功，1-99-取票失败，110-重复取票成功。
     */
    private Integer collectStatus;

    /**
     * 支付交易流水号。
     */
    private String tradeNo;

    /**
     * 支付渠道代码编号。
     */
    private String payChannelCode;

    /**
     * 渠道类型：1-APP端（tradeType03），2-ETC端（已弃用，tradeType06）。
     */
    private String channelType;

    /**
     * 支付状态：0-未支付，1-支付中，100-SUCCESS，1-99-FAIL。
     */
    private Integer payResult;

    /**
     * 支付金额。
     */
    private Integer payAmount;

    /**
     * 支付时间。
     */
    private LocalDateTime payDate;

    /**
     * 退款时间。
     */
    private LocalDateTime refundTms;

    /**
     * 退款金额。
     */
    private Integer refundAmt;

    /**
     * 退款状态：0-未退款，1-退款中，100-SUCCESS，1-99-FAIL。
     */
    private Integer refundResult;

    /**
     * 退款原因。
     */
    private String refundReason;

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

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public Integer getPayResult() {
        return payResult;
    }

    public void setPayResult(Integer payResult) {
        this.payResult = payResult;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public LocalDateTime getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDateTime payDate) {
        this.payDate = payDate;
    }

    public LocalDateTime getRefundTms() {
        return refundTms;
    }

    public void setRefundTms(LocalDateTime refundTms) {
        this.refundTms = refundTms;
    }

    public Integer getRefundAmt() {
        return refundAmt;
    }

    public void setRefundAmt(Integer refundAmt) {
        this.refundAmt = refundAmt;
    }

    public Integer getRefundResult() {
        return refundResult;
    }

    public void setRefundResult(Integer refundResult) {
        this.refundResult = refundResult;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    @Override
    public String toString() {
        return "TicketCollectInfo{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice=" + ticketPrice +
                ", singelTicketNum=" + singelTicketNum +
                ", singleTicketType=" + singleTicketType +
                ", channelCode='" + channelCode + '\'' +
                ", orderStatus=" + orderStatus +
                ", createTms=" + createTms +
                ", deviceId='" + deviceId + '\'' +
                ", collectStatus=" + collectStatus +
                ", tradeNo='" + tradeNo + '\'' +
                ", payResult=" + payResult +
                '}';
    }
}
