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
     * 本次行程消耗的日票次数，恒为 {@code 1}（仅 SIGN_CHANNEL_CODE=12/13/14/15 时写入）。
     *
     * <p>**不是剩余次数**（用户 2026-09-10 裁定语义）。一日票 / 多日票是「这趟用掉一次日票」，
     * 多日计次票是「这趟扣一次」，两者都是 1。**NEVER 回填
     * {@code DAILY_TICKET_INSTANCE.ACTUAL_TIMES}**——那一列用 {@code -99} 表示不限次，
     * 会一路透到 APP 与 {@code GATE_TXN_PAY.COUNTING_TIMES}（2026-09-10 订单
     * {@code GT20260910143159899000084} 已发生）。剩余次数属票卡资产状态，归日票查询接口。
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
     * 扣款结果。
     *
     * <p>NEVER 依赖本字段：全仓库没有任何写入方，本响应里它恒为 null
     * （2026-09-14 全模块 grep 核实 setDebitRequestResult，只命中本类自己的 setter）。
     * 原设计注释写的是「出站完成后由 pay-sign-server 回填」，但闸机检票是同步响应，
     * 免密扣款是出站之后才发起的异步链路，时序上不可能在这个响应里有值。
     *
     * <p>要看扣款结果 MUST 走 IF8A 交易记录接口：权威列是 GATE_TXN_PAY.DEBIT_STATUS，
     * PAY_TXN_DETAIL.DEBIT_REQUEST_RESULT 仅在前者为空时兜底，
     * 映射见 ticket-server TransRecordAssembler.toAppDebitResult。
     *
     * <p>保留不删的原因：本类是 IF1A 检票的对外响应契约，增删字段都要重建链路上全部镜像
     * （model 版本号锁死 2.0.0，见 AGENTS.md §7），删它的收益只有清洁度，不值这个代价。
     */
    private String debitRequestResult;

    /**
     * 签约流水号（来自 USER_ITP_REG_INFO.REQ_CONTRACT_NO），由 ticket-server 查 account-server 后回传。
     *
     * <p>MUST 走响应通道回传：fep-dev-server 传给 ticket-server 的请求对象是跨进程值拷贝，
     * ticket-server 在 applyActualCardType 里改自己那份副本，fep-dev-server 读不到。
     * 出站扣费链路要靠它定位免密扣款协议（2026-08-26 新增）。</p>
     *
     * <p>支付渠道编码没有单独字段：上面的 {@code payChannelCode} 已经承载
     * {@code USER_ITP_REG_INFO.CHANNEL}（见 GateTicketHandler 里的赋值），
     * **NEVER** 再加一个 paymentVendor 字段表达同一个值，否则两者必然漂移。</p>
     */
    private String requestSignSeq;

    /**
     * 第三方支付账户标识（来自 USER_ITP_REG_INFO.THIRD_PAY_ID），钱包支付（0B）扣款用。
     */
    private String payUserId;

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
    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
}
