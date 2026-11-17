package com.chinasofti.huateng.model.app;


public class DeleteBlackListReqDTO {

    private String cardId;


    private String releaseReason;


    private String releaseBy;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public String getReleaseBy() {
        return releaseBy;
    }

    public void setReleaseBy(String releaseBy) {
        this.releaseBy = releaseBy;
    }

    @Override
    public String toString() {
        return "DeleteBlackListReqDTO{cardId='" + cardId + "', releaseReason='" + releaseReason
                + "', releaseBy='" + releaseBy + "'}";
    }
}
