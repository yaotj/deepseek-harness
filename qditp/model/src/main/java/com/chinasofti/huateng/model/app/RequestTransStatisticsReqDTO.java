package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * IF8A-41 查询账单统计请求参数。
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
    /** 卡号列表（MyBatis foreach 使用） */
    private List<String> cardIdList;

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

    public List<String> getCardIdList() {
        return cardIdList;
    }

    public void setCardIdList(List<String> cardIdList) {
        this.cardIdList = cardIdList;
    }

    @Override
    public String toString() {
        return "RequestTransStatisticsReqDTO{cardId='" + cardId
                + "', startDate='" + startDate + "', endDate='" + endDate
                + "', thirdUserId='" + thirdUserId + "'}";
    }
}
