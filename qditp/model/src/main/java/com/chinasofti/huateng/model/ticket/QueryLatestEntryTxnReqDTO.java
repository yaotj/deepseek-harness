package com.chinasofti.huateng.model.ticket;

/**
 * 查「早于本次出站时间的最近一笔进站明细」的入参（离线码出站重算票价用）。
 *
 * <p>为什么新建一个类而不给 {@link QueryFirstEntryTxnReqDTO} 加字段：那个 DTO 是在跑的对外契约，
 * 加字段会让链路上所有未重建的旧镜像被 Fastjson2 静默丢字段（AGENTS.md §7）。两者并存、互不复用。
 *
 * <p><b>NEVER 退回按 {@code ticketTransSeq} 相等配对进出站</b>：进站与出站是同一张卡的两笔不同交易，
 * 闸机上送的 {@code ticketTransSeq} 天然不同（2026-09-22 实测进站 0 / 出站 1），按相等配对恒命中 0 行。
 */
public class QueryLatestEntryTxnReqDTO {
    /** 票卡逻辑卡号。 */
    private String cardId;
    /**
     * 本次出站时间（{@code yyyyMMddHHmmss}）。查询只取 {@code HANDLE_DATE_TIME} 早于或等于该值的进站明细，
     * 这一条就是防误配的关键约束：同卡连续多趟时，晚于本次出站的那笔进站属于下一趟行程、不会被配进来。
     */
    private String exitHandleDateTime;

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getExitHandleDateTime() { return exitHandleDateTime; }
    public void setExitHandleDateTime(String exitHandleDateTime) { this.exitHandleDateTime = exitHandleDateTime; }
}
