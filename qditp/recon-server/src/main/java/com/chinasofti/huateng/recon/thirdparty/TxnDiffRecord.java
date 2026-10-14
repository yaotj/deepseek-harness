package com.chinasofti.huateng.recon.thirdparty;

/**
 * 对账差异明细落地对象（对应 RECON_TXN_DIFF 与 ITP.TXNDIFF.YYYYMMDD 第 1~21 段）。
 *
 * <p>第 1~19 段与 DETAIL 完全一致；第 20 段差异方、第 21 段扣款失败原因由比对层填入。</p>
 */
public class TxnDiffRecord {

    private Long id;
    private String billDate;
    private String txnType;
    private String logicCardNo;
    private String txnDateTime;
    private Long originalFare;
    private String curStationName;
    private String deviceNo;
    private String ticketType;
    private String ticketSubType;
    private String times;
    private String payChannel;
    private String activateTime;
    private String quantity;
    private String ticketTransSeq;
    private String orderNo;
    private Long orderAmount;
    private Long actualAmount;
    private Long discountCumulativeAmount;
    private Long overtimeAmount;
    private String diffSide;
    private String failReason;
    private String batchNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }
    public String getTxnType() { return txnType; }
    public void setTxnType(String txnType) { this.txnType = txnType; }
    public String getLogicCardNo() { return logicCardNo; }
    public void setLogicCardNo(String logicCardNo) { this.logicCardNo = logicCardNo; }
    public String getTxnDateTime() { return txnDateTime; }
    public void setTxnDateTime(String txnDateTime) { this.txnDateTime = txnDateTime; }
    public Long getOriginalFare() { return originalFare; }
    public void setOriginalFare(Long originalFare) { this.originalFare = originalFare; }
    public String getCurStationName() { return curStationName; }
    public void setCurStationName(String curStationName) { this.curStationName = curStationName; }
    public String getDeviceNo() { return deviceNo; }
    public void setDeviceNo(String deviceNo) { this.deviceNo = deviceNo; }
    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }
    public String getTicketSubType() { return ticketSubType; }
    public void setTicketSubType(String ticketSubType) { this.ticketSubType = ticketSubType; }
    public String getTimes() { return times; }
    public void setTimes(String times) { this.times = times; }
    public String getPayChannel() { return payChannel; }
    public void setPayChannel(String payChannel) { this.payChannel = payChannel; }
    public String getActivateTime() { return activateTime; }
    public void setActivateTime(String activateTime) { this.activateTime = activateTime; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getOrderAmount() { return orderAmount; }
    public void setOrderAmount(Long orderAmount) { this.orderAmount = orderAmount; }
    public Long getActualAmount() { return actualAmount; }
    public void setActualAmount(Long actualAmount) { this.actualAmount = actualAmount; }
    public Long getDiscountCumulativeAmount() { return discountCumulativeAmount; }
    public void setDiscountCumulativeAmount(Long discountCumulativeAmount) { this.discountCumulativeAmount = discountCumulativeAmount; }
    public Long getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(Long overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public String getDiffSide() { return diffSide; }
    public void setDiffSide(String diffSide) { this.diffSide = diffSide; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
}
