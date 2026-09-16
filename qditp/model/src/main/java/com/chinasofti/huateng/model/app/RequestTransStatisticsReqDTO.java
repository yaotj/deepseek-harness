package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * IF8A-41 查询账单统计请求参数。
 *
 * <p>APP 实际上送 6 个字段（2026-09-10 抓 fep-app 日志实测）：
 * {@code thirdUserId} / {@code cardType} / {@code transType} / {@code cardId} /
 * {@code startDate} / {@code endDate}。其中：
 * <ul>
 *   <li><b>{@code cardType} 与 {@code transType} 恒同值</b>（实测三个页签分别为 05/05、02/02、03/03），
 *       两个字段一个语义。本 DTO 只接 {@code cardType}，{@code transType} 由 Fastjson2 丢弃。
 *       **NEVER 把 transType 当成另一个查询维度**去加字段或加 SQL 条件。</li>
 *   <li><b>日期格式是 {@code yyyyMMdd}</b>（如 {@code 20260901}），与 {@code QRCODE_TXN_DETAIL.TXN_DATE}
 *       同格式、直接可比。**NEVER 复用 IF8A-05 的 {@code TransQueryHandler.normalizeDate}**——
 *       那个方法按 {@code yyyy-MM-dd} 解析（IF8A-05 的 APP 传的正是带横线的格式），
 *       喂 {@code yyyyMMdd} 会抛 {@code IllegalArgumentException}。两个接口的日期格式**不一致**。</li>
 *   <li><b>未开通某票种时该页签不带 {@code cardId}</b>（实测 NFC 页签只有 thirdUserId + cardType）。
 *       因此 {@code cardType} 是唯一的票种过滤依据，缺了它 SQL 会退化成「按用户查全部票种」，
 *       把其他票种的数据算进来（实测该用户 4 票种时会把 47 条全部合计）。</li>
 * </ul>
 */
public class RequestTransStatisticsReqDTO {
    /** 卡号，支持多个，逗号分隔 */
    private String cardId;
    /** 开始日期 yyyyMMdd */
    private String startDate;
    /** 结束日期 yyyyMMdd */
    private String endDate;
    /** 用户id */
    private String thirdUserId;
    /** APP 上送的卡类型（02/03/04/05/11~15），由 service 映射为 cardTypeList 后清空 */
    private String cardType;
    /** 卡号列表（MyBatis foreach 使用） */
    private List<String> cardIdList;
    /** 发卡卡类型列表（MyBatis foreach 使用），由 CardTypeMapping.toIssueCardTypes 展开 */
    private List<String> cardTypeList;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public List<String> getCardIdList() {
        return cardIdList;
    }

    public void setCardIdList(List<String> cardIdList) {
        this.cardIdList = cardIdList;
    }

    public List<String> getCardTypeList() {
        return cardTypeList;
    }

    public void setCardTypeList(List<String> cardTypeList) {
        this.cardTypeList = cardTypeList;
    }

    @Override
    public String toString() {
        return "RequestTransStatisticsReqDTO{cardId='" + cardId
                + "', startDate='" + startDate + "', endDate='" + endDate
                + "', thirdUserId='" + thirdUserId + "', cardType='" + cardType
                + "', cardTypeList=" + cardTypeList + "}";
    }
}
