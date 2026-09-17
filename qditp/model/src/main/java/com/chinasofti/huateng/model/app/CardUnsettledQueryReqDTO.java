package com.chinasofti.huateng.model.app;

/**
 * 按卡号查询「是否仍有未结清扣费订单」的通用请求。
 */
public class CardUnsettledQueryReqDTO {

    private String cardId;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }
}
