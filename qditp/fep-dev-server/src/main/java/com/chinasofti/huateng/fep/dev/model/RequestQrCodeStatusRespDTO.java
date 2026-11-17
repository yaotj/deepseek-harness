package com.chinasofti.huateng.fep.dev.model;

import com.chinasofti.huateng.common.response.CommonResult;

public class RequestQrCodeStatusRespDTO extends CommonResult {

    private String itpUserId;
    private String cardId;
    private String lastTicketStatus;
    private String lastHandleDateTime;

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getLastTicketStatus() {
        return lastTicketStatus;
    }

    public void setLastTicketStatus(String lastTicketStatus) {
        this.lastTicketStatus = lastTicketStatus;
    }

    public String getLastHandleDateTime() {
        return lastHandleDateTime;
    }

    public void setLastHandleDateTime(String lastHandleDateTime) {
        this.lastHandleDateTime = lastHandleDateTime;
    }
}
