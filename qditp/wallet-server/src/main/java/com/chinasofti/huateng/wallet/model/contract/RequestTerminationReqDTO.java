package com.chinasofti.huateng.wallet.model.contract;

/**
 * IF8A-06 请求解约请求报文。
 */
public class RequestTerminationReqDTO {
    /**
     * 第三方用户编码。
     */
    private String thirdUserId;

    /**
     * 地铁会员卡号。
     */
    private String cardId;

    /**
     * 卡类型编码。
     */
    private String cardType;

    /**
     * 签约请求流水号。
     */
    private String requestSignSeq;

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

    @Override
    public String toString() {
        return "RequestTerminationReqDTO{" +
                "thirdUserId='" + thirdUserId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", cardType='" + cardType + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                '}';
    }
}
