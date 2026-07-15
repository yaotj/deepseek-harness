package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-67 日票激活请求参数。
 */
public class DailyTicketActivateReqDTO {
    /**
     * 第三方用户编号。
     */
    private String thirdUserId;

    /**
     * 操作日期，格式yyyyMMdd。
     */
    private String operationDate;

    /**
     * 有效期天数。
     */
    private Integer period;

    /**
     * 日票订单号。
     */
    private String orderNo;

    /**
     * 发卡机构代码。
     */
    private String cardIssue;

    /**
     * 优惠金额，单位分。
     */
    private Integer discountAmount;

    /**
     * APP侧票类型。
     */
    private String ticketType;

    /**
     * 实际可用次数，-99表示不限次。
     */
    private Integer actualTimes;

    /**
     * 交易序号。
     */
    private Integer transSeq;

    /**
     * 交易金额，单位分。
     */
    private Integer transAmount;

    /**
     * 日票虚拟卡号。
     */
    private String cardNum;

    /**
     * 计次/计时开始时间，毫秒时间戳。
     */
    private Long countingStart;

    /**
     * 交易时间，毫秒时间戳。
     */
    private Long transDate;

    /**
     * 展示票类型。
     */
    private String showType;

    /**
     * 支付渠道。
     */
    private String payChannel;

    /**
     * 车票编码。
     */
    private String ticketCode;

    /**
     * 车票名称。
     */
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
}
