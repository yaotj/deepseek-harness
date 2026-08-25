package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * IF1A-01 闸机检票通知响应。
 */
public class NotifyVerifyResultRespDTO extends CommonResult {

    /**
     * 本次交易后票卡状态码（如：02=进站, 05=出站, 06=出站超时）。
     */
    private String ticketStatus;

    /**
     * 订单异常类型：0=正常(02出站), 5=双段计费正常订单_行程超时(03超时出站)。
     */
    private String orderExpType;

    /**
     * 离线码标识：0x17签约渠道→Y，其他→null。
     */
    private String offlineFlag;

    /**
     * 同行票标识：同行票=Y，第三方=C，其他→null。来自 account 查询。
     */
    private String companionFlag;

    /**
     * 日票票号（仅 SIGN_CHANNEL_CODE=12/13/14/15 时由 ticket-server 查询写入）。
     */
    private String ticketCode;

    /**
     * 计次票剩余可用次数（仅 SIGN_CHANNEL_CODE=12/13/14/15 时写入）。
     */
    private Integer countingTimes;

    /**
     * 计次/计时标识：1=计时票(0445-0447)，2=计次票(0448)。
     */
    private String countingFlag;

    /**
     * 订单应收商户（归属方编码）。
     */
    private String attributableParty;

    /**
     * 订单实收商户（收款方编码）。
     */
    private String receivingParty;

    /**
     * 支付渠道编码（如 ALIPAY、WECHAT）。
     */
    private String payChannelCode;

    /**
     * 优惠金额（单位：分）。
     */
    private String discountFee;

    /**
     * 优惠信息（JSON 字符串）。
     */
    private String discountInfo;

    /**
     * 扣款结果（PROCESSING/SUCCESS/FAIL），出站完成后由 pay-sign-server 回填。
     */
    private String debitRequestResult;

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public String getOrderExpType() {
        return orderExpType;
    }

    public void setOrderExpType(String orderExpType) {
        this.orderExpType = orderExpType;
    }

    public String getOfflineFlag() {
        return offlineFlag;
    }

    public void setOfflineFlag(String offlineFlag) {
        this.offlineFlag = offlineFlag;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

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
    public String getDiscountFee() { return discountFee; }
    public void setDiscountFee(String discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getDebitRequestResult() { return debitRequestResult; }
    public void setDebitRequestResult(String debitRequestResult) { this.debitRequestResult = debitRequestResult; }
}
