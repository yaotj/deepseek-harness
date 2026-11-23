package com.chinasofti.huateng.model.app;


/**
 * ITP -> APP 行程订单通知（R6 表127 /app/orderPayNotice）业务报文。
 *
 * <p>字段名与甲方规格逐字一致：{@code tradeOrderNo}（交易订单号）、{@code payTradeOrderNo}（支付交易订单号）、
 * {@code requestDebitNo}（请求扣款编号）三个号的语义不同，NEVER 合并成一个字段。</p>
 *
 * <p>规范未给码值字典，本地口径：{@code orderStatus} 沿用对外扣款结果两态 {@code 0} 成功 / {@code 2} 失败；
 * {@code payChannelCode} 取订单的支付渠道（PAYMENT_VENDOR）；{@code ticketType} 取卡类型。</p>
 */
public class AppOrderPayNoticeReqDTO {

    private String thirdUserId;

    private String entryStationName;

    private String exitStationName;

    private String entryStationDate;

    private String exitStationDate;

    private String payAmount;

    private String tradeOrderNo;

    private String payTradeOrderNo;

    private String orderExpType;

    private String payChannelCode;

    private String orderStatus;

    private String requestDebitNo;

    private String cardNo;

    private String ticketType;

    private String transSeq;

    private String debitAmount;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getEntryStationName() {
        return entryStationName;
    }

    public void setEntryStationName(String entryStationName) {
        this.entryStationName = entryStationName;
    }

    public String getExitStationName() {
        return exitStationName;
    }

    public void setExitStationName(String exitStationName) {
        this.exitStationName = exitStationName;
    }

    public String getEntryStationDate() {
        return entryStationDate;
    }

    public void setEntryStationDate(String entryStationDate) {
        this.entryStationDate = entryStationDate;
    }

    public String getExitStationDate() {
        return exitStationDate;
    }

    public void setExitStationDate(String exitStationDate) {
        this.exitStationDate = exitStationDate;
    }

    public String getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(String payAmount) {
        this.payAmount = payAmount;
    }

    public String getTradeOrderNo() {
        return tradeOrderNo;
    }

    public void setTradeOrderNo(String tradeOrderNo) {
        this.tradeOrderNo = tradeOrderNo;
    }

    public String getPayTradeOrderNo() {
        return payTradeOrderNo;
    }

    public void setPayTradeOrderNo(String payTradeOrderNo) {
        this.payTradeOrderNo = payTradeOrderNo;
    }

    public String getOrderExpType() {
        return orderExpType;
    }

    public void setOrderExpType(String orderExpType) {
        this.orderExpType = orderExpType;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getRequestDebitNo() {
        return requestDebitNo;
    }

    public void setRequestDebitNo(String requestDebitNo) {
        this.requestDebitNo = requestDebitNo;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(String transSeq) {
        this.transSeq = transSeq;
    }

    public String getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(String debitAmount) {
        this.debitAmount = debitAmount;
    }
}
