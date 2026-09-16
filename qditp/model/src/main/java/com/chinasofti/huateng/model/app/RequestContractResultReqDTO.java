package com.chinasofti.huateng.model.app;

public class RequestContractResultReqDTO {
    private String thirdUserId;
    private String requestSignSeq;
    private String paymentVendor;
    /** 钱包绑定状态查询所需卡信息；传统签约查询可不传。 */
    private String cardId;
    private String cardType;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
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

    @Override
    public String toString() {
        return "RequestContractResultReqDTO{thirdUserId='" + thirdUserId + "', requestSignSeq='" + requestSignSeq
                + "', paymentVendor='" + paymentVendor + "'}";
    }
}
