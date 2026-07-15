package com.chinasofti.huateng.paysign.model.request;

/**
 * IF8A-06 请求解约参数。
 *
 * <p>该对象承接 fep-app 透传的 APP bizData。pay-sign-server 会校验本地签约记录，
 * 再按支付 API 2.3 请求解约接口只取 requestSignSeq 组装下游报文。</p>
 */
public class RequestTerminationReqDTO {
    /** 三方用户 ID，用于定位本地签约记录。 */
    private String thirdUserId;
    /** 卡 ID，用于本地记录和后续 APP 解约结果通知。 */
    private String cardId;
    /** 卡类型，用于本地记录和后续 APP 解约结果通知。 */
    private String cardType;
    /** 签约流水号，支付平台解约接口必填。 */
    private String requestSignSeq;
    /** 支付方式，例如 ALIPAY、WECHAT 或项目约定的渠道编码。 */
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
        return "RequestTerminationReqDTO{" +
                "thirdUserId='" + thirdUserId + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                '}';
    }
}
