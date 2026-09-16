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

    /** 钱包支付用户标识，paymentVendor=0B 时使用。 */
    private String payUserId;

    /**
     * 支付宝出行行业明细 JSON（21 键），仅 {@code issueChannelCode=07} 时由 fep-dev-server 填充。
     *
     * <p>**MUST 由 fep-dev-server 在出站当次组装后整块透传**：其中 entryLineCode / entryLineName /
     * exitLineCode / exitLineName / entryDeviceCode / entryId / exitId / cardNum / cardIssueCode
     * 这 9 项 {@code GATE_TXN_PAY} 没有对应列，需要 para 单查站线信息（批量接口不返回线路字段）、
     * ticket 查进站设备号、以及按 itpUserId + 时间戳现算的进出站 ID —— 出站是唯一能拿全这些值的时点。
     * gate-txn-pay-server 收到后原样落 {@code GATE_TXN_PAY.INDUSTRY_DETAIL}，
     * 供首次扣费与后续重试复用，**NEVER 在下游重算**（重算必然缺那 9 项）。</p>
     *
     * <p>非支付宝渠道恒为 null；此时 {@code PaySignInitiator} 仍走 pay-sign 那条老路，
     * 行业明细用订单快照 {@code JSON.toJSONString(order)}，两套语义 **NEVER 混用**。</p>
     */
    private String industryDetail;

    /**
     * 从 IF1A-01 设备报文搬运本类<b>继承自 {@link NotifyVerifyResultReqDTO} 的那 20 个字段</b>。
     *
     * <p>2026-09-14 从 {@code fep-dev-server} 的 {@code GateTxnPayRequestAssembler.assemble}
     * 原样搬入，**字段清单与取值逐行未改**。搬进来的理由：那 20 行连续 setter 的内聚是
     * 「父子类字段搬运」，属于本 DTO 自己的知识；而 assembler 剩下的部分（透传
     * ticket-server 响应、回填中文站名、赋 industryDetail）是**业务规则**，MUST 继续留在
     * assembler。</p>
     *
     * <p><b>本方法只搬运，NEVER 在此处加任何默认值、trim、大小写归一或空值兜底</b> ——
     * 一旦加了，「设备上送什么就落什么」这条前提就断了，而 `GATE_TXN_PAY` 是对账依据。</p>
     *
     * <p><b>NEVER 把本类独有字段（ticketStatus / orderExpType / offlineFlag / 站名 /
     * industryDetail 等）搬进来</b>：它们的来源不是设备报文，而是 ticket-server 响应与
     * para-server 查询，来源不同不能混在一个搬运方法里。</p>
     *
     * <p>⚠️ **这是新增静态方法、不是新增字段**，因此不受「Fastjson2 静默丢字段」那条约束；
     * 但调用方（当前只有 fep-dev-server）**MUST 用重新 install 过的 `model` 重建镜像**，
     * 否则旧镜像里的本类没有这个方法，启动即 `NoSuchMethodError`。
     * `gate-txn-pay-server` 只反序列化字段、不调本方法，不受影响。</p>
     *
     * @param request 设备上送的 IF1A-01 业务参数，NEVER 传 null
     * @return 只填好继承字段的扣费入参；其余字段由调用方按各自来源补齐
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
