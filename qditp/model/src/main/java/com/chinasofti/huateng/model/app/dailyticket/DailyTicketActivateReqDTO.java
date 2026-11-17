package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketActivateReqDTO {

    private String thirdUserId;


    private String operationDate;


    private Integer period;


    private String orderNo;


    private String cardIssue;


    private Integer discountAmount;


    private String ticketType;


    private Integer actualTimes;


    private Integer transSeq;


    private Integer transAmount;


    private String cardNum;


    private Long countingStart;


    private Long transDate;


    private String showType;


    private String payChannel;


    private String ticketCode;


    private String ticketName;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getOperationDate() {
        return operationDate;
    }

    public void setOperationDate(String operationDate) {
        this.operationDate = operationDate;
    }

    public Integer getPeriod() {
        return period;
    }

    public void setPeriod(Integer period) {
        this.period = period;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCardIssue() {
        return cardIssue;
    }

    public void setCardIssue(String cardIssue) {
        this.cardIssue = cardIssue;
    }

    public Integer getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public Integer getActualTimes() {
        return actualTimes;
    }

    public void setActualTimes(Integer actualTimes) {
        this.actualTimes = actualTimes;
    }

    public Integer getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(Integer transSeq) {
        this.transSeq = transSeq;
    }

    public Integer getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(Integer transAmount) {
        this.transAmount = transAmount;
    }

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public Long getCountingStart() {
        return countingStart;
    }

    public void setCountingStart(Long countingStart) {
        this.countingStart = countingStart;
    }

    public Long getTransDate() {
        return transDate;
    }

    public void setTransDate(Long transDate) {
        this.transDate = transDate;
    }

    public String getShowType() {
        return showType;
    }

    public void setShowType(String showType) {
        this.showType = showType;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public String getTicketName() {
        return ticketName;
    }

    public void setTicketName(String ticketName) {
        this.ticketName = ticketName;
    }

    @Override
    public String toString() {
        return "DailyTicketActivateReqDTO{thirdUserId='" + thirdUserId + "', operationDate='" + operationDate
                + "', period=" + period + ", orderNo='" + orderNo + "', cardIssue='" + cardIssue
                + "', discountAmount=" + discountAmount + ", ticketType='" + ticketType
                + "', actualTimes=" + actualTimes + ", transSeq=" + transSeq + ", transAmount=" + transAmount
                + ", cardNum='" + cardNum + "', countingStart=" + countingStart + ", transDate=" + transDate
                + ", showType='" + showType + "', payChannel='" + payChannel + "', ticketCode='" + ticketCode
                + "', ticketName='" + ticketName + "'}";
    }
}
