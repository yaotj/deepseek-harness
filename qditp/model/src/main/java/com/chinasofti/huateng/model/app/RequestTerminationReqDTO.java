package com.chinasofti.huateng.model.app;


public class RequestTerminationReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String requestSignSeq;
    private String paymentVendor;

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

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    @Override
    public String toString() {
        return "RequestTerminationReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', requestSignSeq='" + requestSignSeq
                + "', paymentVendor='" + paymentVendor + "'}";
    }
}
