package com.chinasofti.huateng.fep.dev.model;

/**
 * IF1A-01 闸机检票通知的<b>设备侧入向契约</b>（AGM 上送的 bizData 形状）。
 *
 * <p><b>2026-09-14 新增。</b>此前本端点直接用 {@code model.ticket.NotifyVerifyResultReqDTO}
 * 解析设备报文，于是**同一个类既是对外契约又是 ticket-server 的对内契约**：
 * ticket-server 内部想加字段就会隐式扩大 AGM 契约，AGM 要加字段又必须去动 {@code model}。
 * 这违反 {@code docs/domain} 那条判据「能被 {@code parseBizData} 解析的 DTO 就是对外契约」。
 * 本模块另两条链路本来就是这么隔的（IF1A-02 有 {@link RequestSynKeyListReqDTO} 映射到
 * {@code model.agm.*}，IF1A-04 有 {@link RequestQrCodeStatusReqDTO} 映射到
 * {@code model.ticket.QueryStatusReqDTO}），本类只是把 IF1A-01 也拉到同一种风格。</p>
 *
 * <p><b>字段是当前 {@code NotifyVerifyResultReqDTO} 的逐一对应，一个不少 —— 这是有意的。</b>
 * 少一个字段就等于**收窄设备契约**：Fastjson2 宽松模式会把目标类里没有的字段静默丢弃、
 * 不报错不告警（AGENTS §7 记过这个机理的真实事故），于是设备明明上送了、下游收到的却是 null。
 * 其中 {@code adviceOpt} / {@code paymentVendor} / {@code requestSignSeq} / {@code channelType}
 * 按现有认知**真实 AGM 不上送**（前者由 ticket-server 的 BOM 补站链路自己生产，后三者来自
 * {@code USER_ITP_REG_INFO}），但它们今天确实能被解析并透传过去，**因此保留、NEVER 顺手删** ——
 * 真要裁剪 MUST 先与闸机侧确认报文清单，单独一轮改。</p>
 *
 * <p><b>加字段 MUST 两边一起加</b>（本类 + {@code model.ticket.NotifyVerifyResultReqDTO}
 * + {@code GateTransactionHandler.toTicketRequest} 的搬运）。漏掉任一处都是静默丢字段，
 * 编译不报错 —— 因此有一条反射比对的单测 {@code NotifyVerifyResultDtoParityTest} 盯着字段名集合，
 * 漏了会直接构建失败。</p>
 */
public class NotifyVerifyResultDeviceReqDTO {

    private String deviceId;
    private String itpUserId;
    private String trxType;
    private String issueChannelCode;
    private String signChannelCode;
    private String cardId;
    private String cardType;
    private String handleDateTime;
    private String handleStationCode;
    private String trxAmount;
    private String overtimeAmount;
    private String lastTicketStatus;
    private String handleResultCode;
    private String lastHandleStationCode;
    private String lastHandleDateTime;
    private String ticketTransSeq;
    /** 补站类型：空=真实检票，01=补进站，02=补出站 */
    private String excessFareType;
    /** BOM 操作类型：默认（空）=真实闸机检票/补站，018=补进站，005=免费更新，006=付费更新 */
    private String adviceOpt;
    /** 预留字段1；HCE 卡交易时为闸机处理后的 64 字节 HCE 卡数据 */
    private String reserve1;
    private String reserve2;
    /** 同行票标识：Y=同行票，C=第三方票，为空表示普通票 */
    private String companionFlag;
    private String paymentVendor;
    private String requestSignSeq;
    /** 交易渠道类型：00闸机、01蓝牙、02BOM、03自助补站 */
    private String channelType;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getItpUserId() { return itpUserId; }
    public void setItpUserId(String itpUserId) { this.itpUserId = itpUserId; }
    public String getTrxType() { return trxType; }
    public void setTrxType(String trxType) { this.trxType = trxType; }
    public String getIssueChannelCode() { return issueChannelCode; }
    public void setIssueChannelCode(String issueChannelCode) { this.issueChannelCode = issueChannelCode; }
    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getHandleDateTime() { return handleDateTime; }
    public void setHandleDateTime(String handleDateTime) { this.handleDateTime = handleDateTime; }
    public String getHandleStationCode() { return handleStationCode; }
    public void setHandleStationCode(String handleStationCode) { this.handleStationCode = handleStationCode; }
    public String getTrxAmount() { return trxAmount; }
    public void setTrxAmount(String trxAmount) { this.trxAmount = trxAmount; }
    public String getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(String overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public String getLastTicketStatus() { return lastTicketStatus; }
    public void setLastTicketStatus(String lastTicketStatus) { this.lastTicketStatus = lastTicketStatus; }
    public String getHandleResultCode() { return handleResultCode; }
    public void setHandleResultCode(String handleResultCode) { this.handleResultCode = handleResultCode; }
    public String getLastHandleStationCode() { return lastHandleStationCode; }
    public void setLastHandleStationCode(String lastHandleStationCode) { this.lastHandleStationCode = lastHandleStationCode; }
    public String getLastHandleDateTime() { return lastHandleDateTime; }
    public void setLastHandleDateTime(String lastHandleDateTime) { this.lastHandleDateTime = lastHandleDateTime; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }

    /**
     * 兼容甲方规范样例里的错误拼写 {@code tikcetTransSeq}。
     *
     * <p><b>NEVER 删</b>：{@code model.ticket.NotifyVerifyResultReqDTO} 早就有这个别名 setter，
     * 说明真实设备（或某版本的设备）就是按这个拼写发的。本类是 IF1A-01 的入向解析目标，
     * 少了它等于该字段静默丢失。</p>
     */
    public void setTikcetTransSeq(String tikcetTransSeq) { this.ticketTransSeq = tikcetTransSeq; }

    public String getExcessFareType() { return excessFareType; }
    public void setExcessFareType(String excessFareType) { this.excessFareType = excessFareType; }
    public String getAdviceOpt() { return adviceOpt; }
    public void setAdviceOpt(String adviceOpt) { this.adviceOpt = adviceOpt; }
    public String getReserve1() { return reserve1; }
    public void setReserve1(String reserve1) { this.reserve1 = reserve1; }
    public String getReserve2() { return reserve2; }
    public void setReserve2(String reserve2) { this.reserve2 = reserve2; }
    public String getCompanionFlag() { return companionFlag; }
    public void setCompanionFlag(String companionFlag) { this.companionFlag = companionFlag; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
}
