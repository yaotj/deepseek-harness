package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;


public class AlipayTripReceiveBlackListReqDTO {


    private String thirdUserId;


    private String cardId;


    private String blacklistStatus;

    private String reason;

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

    public String getBlacklistStatus() {
        return blacklistStatus;
    }

    public void setBlacklistStatus(String blacklistStatus) {
        this.blacklistStatus = blacklistStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
