package com.chinasofti.huateng.model.app;

/**
 * IF8B 多日票次数扣减通知（{@code /app/receiveCountingTicketTimes}）的业务参数。
 *
 * <p>字段以甲方规格 {@code 青岛地铁-ITP与APP接口规范R6.docx} §3.63 表129 为准，
 * 全部 4 个字段一个不多一个不少 —— **本 DTO 是对外契约，NEVER 加字段**。
 * 特别注意：**NEVER 在这里补 {@code signType} / {@code sign}** ——
 * 那两个是 form-data 外层的公共参数、由 {@code NotifyFormRequestFactory} 统一提供；
 * 同族的 {@code AppIndustryDataNotifyReqDTO} 在 bizData 里重复带了这两个字段，
 * 属该链路的历史形态，**NEVER 照抄过来**。
 *
 * <p>{@code times} 规格原文注「扣减次数（最大值为2）」，但按用户 2026-09-17 裁决**恒传 1**：
 * 我方 {@code DailyTicketServiceImpl.markUsed} 一次固定扣 1、没有「扣几次」入参。
 * 若日后确实出现一次扣 2 的场景，**MUST 先改扣次实现与发起方归属**（见 AGENTS.md §2.2.2 该条），
 * NEVER 只把这里的值改成 2。
 */
public class AppCountingTicketTimesNotifyReqDTO {

    /** 第三方用户 ID，取闸机上送的 {@code itpUserId}。 */
    private String thirdUserId;

    /** 卡号（逻辑卡号）。 */
    private String cardId;

    /**
     * 交易序列号。**MUST 取闸机上送的 {@code ticketTransSeq}**，
     * NEVER 取 {@code QRCODE_STATUS.TXN_SEQ} —— 后者是 CAS 推进后的值、比前者大 1。
     */
    private String transSeq;

    /** 本次扣减次数，恒为 {@code "1"}。 */
    private String times;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(String transSeq) {
        this.transSeq = transSeq;
    }

    public String getTimes() {
        return times;
    }

    public void setTimes(String times) {
        this.times = times;
    }

    @Override
    public String toString() {
        return "AppCountingTicketTimesNotifyReqDTO{thirdUserId='" + thirdUserId
                + "', cardId='" + cardId
                + "', transSeq='" + transSeq
                + "', times='" + times + "'}";
    }
}
