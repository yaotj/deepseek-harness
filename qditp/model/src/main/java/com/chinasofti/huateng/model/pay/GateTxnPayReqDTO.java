package com.chinasofti.huateng.model.pay;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;

/**
 * 过闸扣费交易请求。
 *
 * <p>该对象由 fep-dev 在收到 IF1A-01 出站/超时出站交易且 ticket 处理成功后转发给
 * gate-txn-pay-server。字段沿用设备闸机检票通知业务参数，gate-txn-pay-server
 * 负责生成地铁侧 {@code orderNo}、落库 {@code GATE_TXN_PAY}。普通车票交易再调用
 * pay-sign-server；日票交易直接默认支付成功。</p>
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
     * 订单应收商户（归属方编码）。
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
}
