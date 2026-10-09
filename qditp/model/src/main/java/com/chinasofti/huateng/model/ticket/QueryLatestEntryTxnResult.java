package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 「早于本次出站时间的最近一笔进站明细」的计费所需字段。
 *
 * <p>数据源是历史流水表 {@code QRCODE_TXN_DETAIL}，<b>NEVER 改用 {@code QRCODE_STATUS.GATE_IN_STATION} /
 * {@code GATE_IN_TIME}</b>：那两列是票卡的当前状态快照、会被下一趟行程覆盖，而离线码补偿是延迟执行的，
 * 延迟期间该卡再进站一次就会拿到新行程的进站信息去算上一笔的钱 —— 那比「算不出」更坏（算错钱）。
 *
 * <p>与 {@link QueryFirstEntryTxnResult} 字段同形但<b>不复用、不共享父类</b>：两者是两套查询口径的独立契约。
 */
public class QueryLatestEntryTxnResult extends CommonResult {
    private String cardId;
    private String ticketTransSeq;
    private String handleDateTime;
    private String handleStationCode;

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getHandleDateTime() { return handleDateTime; }
    public void setHandleDateTime(String handleDateTime) { this.handleDateTime = handleDateTime; }
    public String getHandleStationCode() { return handleStationCode; }
    public void setHandleStationCode(String handleStationCode) { this.handleStationCode = handleStationCode; }
}
