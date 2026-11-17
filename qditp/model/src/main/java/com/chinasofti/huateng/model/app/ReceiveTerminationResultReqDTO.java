package com.chinasofti.huateng.model.app;


public class ReceiveTerminationResultReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String requestSignSeq;
    private String agreementNo;
    private String payAgreementNo;
    private String paymentVendor;
    private String status;
    private String dismissalTime;

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

    public String getAgreementNo() {
        return agreementNo;
    }

    public void setAgreementNo(String agreementNo) {
        this.agreementNo = agreementNo;
    }

    public String getPayAgreementNo() {
        return payAgreementNo;
    }

    public void setPayAgreementNo(String payAgreementNo) {
        this.payAgreementNo = payAgreementNo;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDismissalTime() {
        return dismissalTime;
    }

    public void setDismissalTime(String dismissalTime) {
        this.dismissalTime = dismissalTime;
    }

    @Override
    public String toString() {
        return "ReceiveTerminationResultReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', requestSignSeq='" + requestSignSeq
                + "', agreementNo='" + agreementNo + "', payAgreementNo='" + payAgreementNo
                + "', paymentVendor='" + paymentVendor + "', status='" + status
                + "', dismissalTime='" + dismissalTime + "'}";
    }
}
