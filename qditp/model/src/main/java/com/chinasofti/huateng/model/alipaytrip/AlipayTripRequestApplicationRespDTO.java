package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;


public class AlipayTripRequestApplicationRespDTO extends CommonResult {


    private String cardId;


    private String cardType;


    private String status;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
