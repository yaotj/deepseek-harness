package com.chinasofti.huateng.collectticket.model.response;

/**
 * IF2A-02 查询取票订单状态响应报文。
 */
public class QueryTicketCollectOrderRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

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
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 订单状态：0-未支付，100-支付成功，1-99-支付失败或异常。
     */
    private Integer orderStatus;

    /**
     * 取票状态：0-未取票，100-取票成功，1-99-取票失败，110-重复取票成功。
     */
    private Integer collectStatus;

    /**
     * 取票设备编号。
     */
    private String deviceId;

    /**
     * 设备取票二维码生成时间。
     */
    private String qrcodeGenDate;

    /**
     * 设备随机因子。
     */
    private String randomFact;

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
    private String payDate;

    /**
     * 实际出票数量。
     */
    private Integer actualTakeTicketNum;

    /**
     * 错误代码。
     */
    private String errorCode;

    /**
     * 错误详细信息。
     */
    private String errorMessage;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

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

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
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

    public String getPayDate() {
        return payDate;
    }

    public void setPayDate(String payDate) {
        this.payDate = payDate;
    }

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
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
        return "QueryTicketCollectOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", orderStatus=" + orderStatus +
                ", collectStatus=" + collectStatus +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
