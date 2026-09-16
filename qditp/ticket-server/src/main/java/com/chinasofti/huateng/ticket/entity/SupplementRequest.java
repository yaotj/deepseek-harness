package com.chinasofti.huateng.ticket.entity;

import java.time.LocalDateTime;

/**
 * IF5A-03 补站请求台账（{@code QRCODE_SUPPLEMENT_REQUEST}）。
 *
 * <p>存在的两个理由（都来自 2026-09-14 的 CardDataHandler 审查）：</p>
 * <ol>
 *   <li><b>幂等</b>：唯一索引 {@code UK_QSR_CARD_SEQ_ADVICE} 让同一
 *       {@code (CARD_ID, TXN_SEQ, ADVICE_OPT)} 只有一条请求能进入闸机下发。
 *       此前只做「下发前再 select 一次比对快照」，是纯 TOCTOU，两条并发请求都能通过。</li>
 *   <li><b>结果未知留证据</b>：闸机超时 / 连不上时，闸机侧可能已推进 {@code QRCODE_STATUS}，
 *       而 ITP 侧原来只打一行 {@code log.error}、库里零证据，事后无法对账也无法补偿。
 *       现在这一行会留在 {@code UNKNOWN} 状态等人工或扫表处置。</li>
 * </ol>
 *
 * <p><b>本表 NEVER 参与状态机推进</b>：{@code QRCODE_STATUS} / {@code QRCODE_TXN_DETAIL}
 * 的唯一写入方仍是 {@code gate.GateTicketWriter}（fep-dev-server 回调那个独立请求）。
 * 本表只记「BOM 请求过什么、我方下发了没有、结果是什么」。</p>
 */
public class SupplementRequest {

    /** 处理中，已声明但闸机结果尚未回来。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 闸机返回 0000。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 闸机明确拒绝（非 0000），结果确定，可由 BOM 重新发起。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 结果未知（超时 / 连不上 / 无响应体），<b>NEVER 允许直接重试</b>，MUST 先查票卡状态。 */
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private String cardId;
    private String txnSeq;
    private String adviceOpt;
    private String codeStatusSnapshot;
    private String handleStationCode;
    private String handleDateTime;
    private String trxAmount;
    private String handleStatus;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTxnSeq() {
        return txnSeq;
    }

    public void setTxnSeq(String txnSeq) {
        this.txnSeq = txnSeq;
    }

    public String getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(String adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    public String getCodeStatusSnapshot() {
        return codeStatusSnapshot;
    }

    public void setCodeStatusSnapshot(String codeStatusSnapshot) {
        this.codeStatusSnapshot = codeStatusSnapshot;
    }

    public String getHandleStationCode() {
        return handleStationCode;
    }

    public void setHandleStationCode(String handleStationCode) {
        this.handleStationCode = handleStationCode;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(String trxAmount) {
        this.trxAmount = trxAmount;
    }

    public String getHandleStatus() {
        return handleStatus;
    }

    public void setHandleStatus(String handleStatus) {
        this.handleStatus = handleStatus;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
