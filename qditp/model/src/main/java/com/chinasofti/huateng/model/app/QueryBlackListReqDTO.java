package com.chinasofti.huateng.model.app;

/**
 * 查询黑名单请求参数。
 */
public class QueryBlackListReqDTO {
    /**
     * 卡号，支持多个，逗号分隔。
     */
    private String cardId;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }
}
