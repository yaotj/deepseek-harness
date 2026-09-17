package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * IF8A-41 查询账单统计请求参数。
 */
public class RequestTransStatisticsReqDTO {
    /** 卡号，支持多个，逗号分隔。 */
    private String cardId;
    /** 开始日期 yyyyMMdd。 */
    private String startDate;
    /** 结束日期 yyyyMMdd。 */
    private String endDate;
    /** 用户id。 */
    private String thirdUserId;
    /** APP 上送的卡类型（02/03/04/05/11~15），由 service 映射为 cardTypeList 后清空。 */
    private String cardType;
    /** 卡号列表（MyBatis foreach 使用） */
    private List<String> cardIdList;
    /** 发卡卡类型列表（MyBatis foreach 使用），由 CardTypeMapping.toIssueCardTypes 展开。 */
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
