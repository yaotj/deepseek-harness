package com.chinasofti.huateng.model.ticket;

/**
 * @date 2026/5/25 14:26。
 * @author zzm。
 */
public class QueryStatusReqDTO {

    private String thirdUserId;

    private String cardId;

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
}
