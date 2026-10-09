package com.chinasofti.huateng.model.pay;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;

/**
 * 过闸扣费交易请求。
 */
public class GateTxnPayReqDTO extends NotifyVerifyResultReqDTO {

    /**
     * 本次交易后票卡状态码（由 ticket-server 返回，如：02=进站, 05=出站）。
     */
    private String ticketStatus;

    /**
     * 订单异常类型：由 ticket-server 根据 trxType 计算返回。
     */
    private String orderExpType;

    /**
     * 离线码标识：由 ticket-server 根据 signChannelCode 判断（0x17=Y）。
     */
    private String offlineFlag;

    /**
     * 进站车站名称（由 fep-dev-server 查询 para-server 获取）。
     */
    private String entryStationName;

    /**
     * 出站车站名称（由 fep-dev-server 查询 para-server 获取）。
     */
    private String exitStationName;

    /**
     * 同行票标识：Y/N。
     */
    private String companionFlag;

    /**
     * 日票票号（SIGN_CHANNEL_CODE=12/13/14/15 时由 ticket-server 填充）。
     */
    private String ticketCode;

    /**
     * 计次票剩余可用次数（出站后由 daily-ticket-server 返回）。
     */
    private Integer countingTimes;

    /**
     * 计次/计时标识：1=计时票(0445-0447)，2=计次票(0448)。
     */
    private String countingFlag;

    /**
     * 订单应收商户（归。
     */
    private String attributableParty;

    /**
     * 订单实收商户（收款方编码）。
     */
    private String receivingParty;

    /**
     * 支付通道编码（signChannelCode，由 fep-dev-server 透传）。
     */
    private String payChannelCode;

    /**
     * 优惠金额（分），扣费前由 ticket-server 从订单信息中计算。
     */
    private Integer discountFee;

    /**
     * 优惠详情 JSON 数组，扣费前由 ticket-server 从订单信息中计算。
     */
    private String discountInfo;

    /** 钱包支付用户标识，paymentVendor=0B 时使用。 */
    private String payUserId;

    /**
     * 支付宝出行行业明细 JSON（21 键），仅 {@code issueChannelCode=07} 时由 fep-dev-server 填充。
     */
    private String industryDetail;

    /**
     * 从 IF1A-01 设备报文搬运本类继承自 {@link NotifyVerifyResultReqDTO} 的那 21 个字段。
     *
     * <p>{@code adviceOpt} 是第 21 个、也是最后加入的一个：它决定 gate-txn-pay 侧要不要发起扣款。
     * 非空即 BOM 补站（{@code 005} / {@code 006} / {@code 020}），钱是 BOM 现场收的，
     * 落单但 **NEVER 由 ITP 再扣一次**；空值才是闸机真实检票与 APP 自助补站，走正常后付费。
     * **NEVER 从拷贝列表里删掉它** —— 漏掉这一行时 006 会被当成普通出站免密扣款，乘客重复付费。
     *
     * @param request 设备上送的 IF1A-01 业务参数。
     * @return 只填好继承字段的扣费入参；其余字段由调用方按各自来源补齐。
     */
    public static GateTxnPayReqDTO fromVerifyResult(NotifyVerifyResultReqDTO request) {
        GateTxnPayReqDTO payRequest = new GateTxnPayReqDTO();
        payRequest.setDeviceId(request.getDeviceId());
        payRequest.setItpUserId(request.getItpUserId());
        payRequest.setTrxType(request.getTrxType());
        payRequest.setIssueChannelCode(request.getIssueChannelCode());
        payRequest.setSignChannelCode(request.getSignChannelCode());
        payRequest.setCardId(request.getCardId());
        payRequest.setCardType(request.getCardType());
        payRequest.setHandleDateTime(request.getHandleDateTime());
        payRequest.setHandleStationCode(request.getHandleStationCode());
        payRequest.setTrxAmount(request.getTrxAmount());
        payRequest.setOvertimeAmount(request.getOvertimeAmount());
        payRequest.setLastTicketStatus(request.getLastTicketStatus());
        payRequest.setHandleResultCode(request.getHandleResultCode());
        payRequest.setLastHandleStationCode(request.getLastHandleStationCode());
        payRequest.setLastHandleDateTime(request.getLastHandleDateTime());
        payRequest.setTicketTransSeq(request.getTicketTransSeq());
        payRequest.setReserve1(request.getReserve1());
        payRequest.setReserve2(request.getReserve2());
        payRequest.setChannelType(request.getChannelType());
        payRequest.setCompanionFlag(request.getCompanionFlag());
        payRequest.setAdviceOpt(request.getAdviceOpt());
        return payRequest;
    }

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
    public Integer getDiscountFee() { return discountFee; }
    public void setDiscountFee(Integer discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public String getIndustryDetail() { return industryDetail; }
    public void setIndustryDetail(String industryDetail) { this.industryDetail = industryDetail; }
}
