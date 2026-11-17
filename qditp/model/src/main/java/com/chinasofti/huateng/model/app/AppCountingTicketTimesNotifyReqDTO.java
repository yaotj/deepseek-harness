package com.chinasofti.huateng.model.app;


public class AppCountingTicketTimesNotifyReqDTO {

    private String thirdUserId;

    private String cardId;


    private String transSeq;

    private String times;

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

    public String getTransSeq() {
        return transSeq;
    }

    public void setTransSeq(String transSeq) {
        this.transSeq = transSeq;
    }

    public String getTimes() {
        return times;
    }

    public void setTimes(String times) {
        this.times = times;
    }

    @Override
    public String toString() {
        return "AppCountingTicketTimesNotifyReqDTO{thirdUserId='" + thirdUserId
                + "', cardId='" + cardId
                + "', transSeq='" + transSeq
                + "', times='" + times + "'}";
    }
}
