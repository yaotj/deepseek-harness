package com.chinasofti.huateng.model.app;


public class AppTerminationResultNotifyReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String requestSignSeq;
    private String terminationResult;
    private String terminationResultMsg;
    private String terminationTime;

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

    public String getTerminationResult() {
        return terminationResult;
    }

    public void setTerminationResult(String terminationResult) {
        this.terminationResult = terminationResult;
    }

    public String getTerminationResultMsg() {
        return terminationResultMsg;
    }

    public void setTerminationResultMsg(String terminationResultMsg) {
        this.terminationResultMsg = terminationResultMsg;
    }

    public String getTerminationTime() {
        return terminationTime;
    }

    public void setTerminationTime(String terminationTime) {
        this.terminationTime = terminationTime;
    }

    @Override
    public String toString() {
        return "AppTerminationResultNotifyReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', requestSignSeq='" + requestSignSeq
                + "', terminationResult='" + terminationResult + "', terminationResultMsg='" + terminationResultMsg
                + "', terminationTime='" + terminationTime + "'}";
    }
}
