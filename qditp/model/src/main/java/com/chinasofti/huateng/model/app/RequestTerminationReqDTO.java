package com.chinasofti.huateng.model.app;

/**
 * IF8A-06 请求解约请求参数。
 */
public class RequestTerminationReqDTO {
    /** 三方用户 ID，用于定位本地签约记录。 */
    private String thirdUserId;
    /** 卡 ID，用于记录和后续 APP 通知透传。 */
    private String cardId;
    /** 卡类型，用于记录和后续 APP 通知透传。 */
    private String cardType;
    /** 签约流水号，支付平台请求解约接口的核心参数。 */
    private String requestSignSeq;
    /** 支付方式，例如 ALIPAY、WECHAT 等。 */
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
