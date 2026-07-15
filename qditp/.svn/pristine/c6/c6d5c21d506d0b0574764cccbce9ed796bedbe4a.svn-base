package com.chinasofti.huateng.model.app;

/**
 * 支付 API 5.3 解约回调业务参数。
 *
 * <p>支付平台文档只要求回传 requestSignSeq、协议号、状态、解约时间和支付方式。
 * thirdUserId/cardId/cardType 为 ITP 内部透传字段，如果回调方未传，pay-sign-server
 * 会通过 requestSignSeq/paymentVendor 反查本地签约记录补齐。</p>
 */
public class ReceiveTerminationResultReqDTO {
    /** 三方用户 ID，内部透传字段。 */
    private String thirdUserId;
    /** 卡 ID，内部透传字段。 */
    private String cardId;
    /** 卡类型，内部透传字段。 */
    private String cardType;
    /** 商户端签约流水号。 */
    private String requestSignSeq;
    /** 支付平台协议号。 */
    private String agreementNo;
    /** 支付渠道协议号。 */
    private String payAgreementNo;
    /** 支付方式。 */
    private String paymentVendor;
    /** 解约状态，SUCCESS 表示解约成功。 */
    private String status;
    /** 解约时间，格式 yyyyMMddHHmmss。 */
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
}
