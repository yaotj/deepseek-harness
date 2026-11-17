package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;


public class RegisterRideStatusRespDTO extends CommonResult {

    private String cardId;
    private String itpUserId;
    private String cardStatus;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }
}
