package com.chinasofti.huateng.gatetxnpay.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GATE_TXN_PAY 过闸扣费业务订单实体。
 */
public class GateTxnPay {
    private Long id;
    private String orderNo;
    private String debitStatus;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String deviceId;
    private String trxType;
    private String ticketTransSeq;
    private String inStation;
    private String inTime;
    private String outStation;
    private String outTime;
    private String txnDate;
    private Integer trxAmount;
    private Integer overtimeAmount;
    private Integer totalAmount;
    private String issueChannelCode;
    private String signChannelCode;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 新增字段：if8a_29/if8a_34 查询接口
    private String ticketStatus;         // 票卡状态：01无交易,04进站失败,05已进站,06已出站,07超时出站,70异常
    private String entryStationName;     // 进站车站名称
    private String exitStationName;      // 出站车站名称
    private String orderExpType;         // 订单异常类型：0正常,1单边,2补站
    private String companionFlag;        // 陪同票标志：Y是,N否（来自USER_ITP_REG_INFO）
    private String offlineFlag;          // 离线码标志：Y是,N否
    // 日票额外字段
    private String ticketCode;           // 日票票号
    private Integer countingTimes;       // 计次票剩余次数（扣减后）
    private String countingFlag;         // 计次票标志：Y是,N否
    private String attributableParty;    // 订单应收商户（cjdsj/qddt）
    private String receivingParty;       // 订单实收商户（cjdsj/qddt）
    private String payChannelCode;       // 支付渠道编码（如 ALIPAY、WECHAT，来自 USER_ITP_REG_INFO.CHANNEL）
    private String paymentVendor;        // 支付厂商编码，钱包为 0B
    private String channelType;          // 交易渠道类型，01=蓝牙
    private String payUserId;            // 钱包扣款用户标识
    private String transferFlag;         // 换乘标识：01无换乘，02有换乘
    private String cumulativeType;       // 钱包累计类型：01正常出站，02超时出站，03不累计
    private Integer originalFare;        // 进出站地铁原价，单位分
    private Integer walletTotalAmt;      // 钱包当前累计金额，单位分
    private Integer discountLevelAmt;    // 命中的累计金额门槛，单位分
    private BigDecimal discountRate;     // 命中的折扣率
    private Integer expectedGateAmount;  // 按折扣公式计算的期望闸机金额，单位分
    private String discountCalcStatus;   // SUCCESS/FALLBACK/SKIPPED
    private String discountCalcMsg;      // 钱包优惠计算说明或降级原因
    private String industryDetail;       // 支付宝出行行业明细JSON（21键），仅 issueChannelCode=07 有值，落单时整块存下、扣费与重试复用

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getDebitStatus() { return debitStatus; }
    public void setDebitStatus(String debitStatus) { this.debitStatus = debitStatus; }
    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTrxType() { return trxType; }
    public void setTrxType(String trxType) { this.trxType = trxType; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getInStation() { return inStation; }
    public void setInStation(String inStation) { this.inStation = inStation; }
    public String getInTime() { return inTime; }
    public void setInTime(String inTime) { this.inTime = inTime; }
    public String getOutStation() { return outStation; }
    public void setOutStation(String outStation) { this.outStation = outStation; }
    public String getOutTime() { return outTime; }
    public void setOutTime(String outTime) { this.outTime = outTime; }
    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }
    public Integer getTrxAmount() { return trxAmount; }
    public void setTrxAmount(Integer trxAmount) { this.trxAmount = trxAmount; }
    public Integer getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(Integer overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public String getIssueChannelCode() { return issueChannelCode; }
    public void setIssueChannelCode(String issueChannelCode) { this.issueChannelCode = issueChannelCode; }
    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // 新增字段 getter/setter
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getEntryStationName() { return entryStationName; }
    public void setEntryStationName(String entryStationName) { this.entryStationName = entryStationName; }
    public String getExitStationName() { return exitStationName; }
    public void setExitStationName(String exitStationName) { this.exitStationName = exitStationName; }
    public String getOrderExpType() { return orderExpType; }
    public void setOrderExpType(String orderExpType) { this.orderExpType = orderExpType; }
    public String getCompanionFlag() { return companionFlag; }
    public void setCompanionFlag(String companionFlag) { this.companionFlag = companionFlag; }
    public String getOfflineFlag() { return offlineFlag; }
    public void setOfflineFlag(String offlineFlag) { this.offlineFlag = offlineFlag; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public Integer getCountingTimes() { return countingTimes; }
    public void setCountingTimes(Integer countingTimes) { this.countingTimes = countingTimes; }
    public String getCountingFlag() { return countingFlag; }
    public void setCountingFlag(String countingFlag) { this.countingFlag = countingFlag; }
    public String getAttributableParty() { return attributableParty; }
    public void setAttributableParty(String attributableParty) { this.attributableParty = attributableParty; }
    public String getReceivingParty() { return receivingParty; }
    public void setReceivingParty(String receivingParty) { this.receivingParty = receivingParty; }
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public String getTransferFlag() { return transferFlag; }
    public void setTransferFlag(String transferFlag) { this.transferFlag = transferFlag; }
    public String getCumulativeType() { return cumulativeType; }
    public void setCumulativeType(String cumulativeType) { this.cumulativeType = cumulativeType; }
    public Integer getOriginalFare() { return originalFare; }
    public void setOriginalFare(Integer originalFare) { this.originalFare = originalFare; }
    public Integer getWalletTotalAmt() { return walletTotalAmt; }
    public void setWalletTotalAmt(Integer walletTotalAmt) { this.walletTotalAmt = walletTotalAmt; }
    public Integer getDiscountLevelAmt() { return discountLevelAmt; }
    public void setDiscountLevelAmt(Integer discountLevelAmt) { this.discountLevelAmt = discountLevelAmt; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
    public Integer getExpectedGateAmount() { return expectedGateAmount; }
    public void setExpectedGateAmount(Integer expectedGateAmount) { this.expectedGateAmount = expectedGateAmount; }
    public String getDiscountCalcStatus() { return discountCalcStatus; }
    public void setDiscountCalcStatus(String discountCalcStatus) { this.discountCalcStatus = discountCalcStatus; }
    public String getDiscountCalcMsg() { return discountCalcMsg; }
    public void setDiscountCalcMsg(String discountCalcMsg) { this.discountCalcMsg = discountCalcMsg; }
    public String getIndustryDetail() { return industryDetail; }
    public void setIndustryDetail(String industryDetail) { this.industryDetail = industryDetail; }
}
