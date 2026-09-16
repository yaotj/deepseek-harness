package com.chinasofti.huateng.model.app;

import java.math.BigDecimal;

/**
 * IF8A-05 单条交易记录。
 */
public class TransRecordDTO {
    private String entryStationName;
    private String entryDate;
    private String exitStationName;
    private String exitDate;
    private String payAmount;
    private String orderExpType;
    private String tradeOrderNo;
    private String payTradeOrderNo;
    private String payOrderNoDate;
    private String debitRequestResult;
    private Integer discountFee;
    private String discountInfo;
    private String companionFlag;
    private String cardNum;
    private String ticketCode;
    private Integer countingTimes;
    private String countingFlag;
    private String offlineFlag;
    private String attributableParty;
    private String receivingParty;
    /** 支付渠道编码（如 03=支付宝、05=微信），映射自 PAY_TXN_DETAIL.PAYMENT_VENDOR */
    private String payChannelCode;
    private String transferFlag;
    private String cumulativeType;
    private Integer originalFare;
    /**
     * 原始票价（分），值与 {@link #originalFare} 完全相同，供 APP 按 totalAmount 取用。
     *
     * <p><b>NEVER 把它当成 {@code GATE_TXN_PAY.TOTAL_AMOUNT}</b>（那是实付 = 车费 + 超时费，
     * 对应本 DTO 的 {@code payAmount}）。这里只是 APP 要求的字段名，语义仍是「进出站地铁原价」，
     * 赋值处 MUST 与 {@code originalFare} 同源，两者不允许出现不同值。</p>
     */
    private Integer totalAmount;
    private Integer walletTotalAmt;
    private Integer discountLevelAmt;
    private BigDecimal discountRate;
    private Integer expectedGateAmount;

    public String getEntryStationName() { return entryStationName; }
    public void setEntryStationName(String entryStationName) { this.entryStationName = entryStationName; }
    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }
    public String getExitStationName() { return exitStationName; }
    public void setExitStationName(String exitStationName) { this.exitStationName = exitStationName; }
    public String getExitDate() { return exitDate; }
    public void setExitDate(String exitDate) { this.exitDate = exitDate; }
    public String getPayAmount() { return payAmount; }
    public void setPayAmount(String payAmount) { this.payAmount = payAmount; }
    public String getOrderExpType() { return orderExpType; }
    public void setOrderExpType(String orderExpType) { this.orderExpType = orderExpType; }
    public String getTradeOrderNo() { return tradeOrderNo; }
    public void setTradeOrderNo(String tradeOrderNo) { this.tradeOrderNo = tradeOrderNo; }
    public String getPayTradeOrderNo() { return payTradeOrderNo; }
    public void setPayTradeOrderNo(String payTradeOrderNo) { this.payTradeOrderNo = payTradeOrderNo; }
    public String getPayOrderNoDate() { return payOrderNoDate; }
    public void setPayOrderNoDate(String payOrderNoDate) { this.payOrderNoDate = payOrderNoDate; }
    public String getDebitRequestResult() { return debitRequestResult; }
    public void setDebitRequestResult(String debitRequestResult) { this.debitRequestResult = debitRequestResult; }
    public Integer getDiscountFee() { return discountFee; }
    public void setDiscountFee(Integer discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getCompanionFlag() { return companionFlag; }
    public void setCompanionFlag(String companionFlag) { this.companionFlag = companionFlag; }
    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public Integer getCountingTimes() { return countingTimes; }
    public void setCountingTimes(Integer countingTimes) { this.countingTimes = countingTimes; }
    public String getCountingFlag() { return countingFlag; }
    public void setCountingFlag(String countingFlag) { this.countingFlag = countingFlag; }
    public String getOfflineFlag() { return offlineFlag; }
    public void setOfflineFlag(String offlineFlag) { this.offlineFlag = offlineFlag; }
    public String getAttributableParty() { return attributableParty; }
    public void setAttributableParty(String attributableParty) { this.attributableParty = attributableParty; }
    public String getReceivingParty() { return receivingParty; }
    public void setReceivingParty(String receivingParty) { this.receivingParty = receivingParty; }
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getTransferFlag() { return transferFlag; }
    public void setTransferFlag(String transferFlag) { this.transferFlag = transferFlag; }
    public String getCumulativeType() { return cumulativeType; }
    public void setCumulativeType(String cumulativeType) { this.cumulativeType = cumulativeType; }
    public Integer getOriginalFare() { return originalFare; }
    public void setOriginalFare(Integer originalFare) { this.originalFare = originalFare; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public Integer getWalletTotalAmt() { return walletTotalAmt; }
    public void setWalletTotalAmt(Integer walletTotalAmt) { this.walletTotalAmt = walletTotalAmt; }
    public Integer getDiscountLevelAmt() { return discountLevelAmt; }
    public void setDiscountLevelAmt(Integer discountLevelAmt) { this.discountLevelAmt = discountLevelAmt; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
    public Integer getExpectedGateAmount() { return expectedGateAmount; }
    public void setExpectedGateAmount(Integer expectedGateAmount) { this.expectedGateAmount = expectedGateAmount; }
}
