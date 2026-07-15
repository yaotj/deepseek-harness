package com.chinasofti.huateng.wallet.model.contract;

/**
 * IF8A-22 签约结果咨询请求报文。
 */
public class RequestContractResultReqDTO {
    /**
     * 第三方用户编码。
     */
    private String thirdUserId;

    /**
     * 签约请求流水号。
     */
    private String requestSignSeq;

    /**
     * 支付类型。
     */
    private String paymentVendor;

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

    @Override
    public String toString() {
        return "RequestContractResultReqDTO{" +
                "thirdUserId='" + thirdUserId + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                '}';
    }
}
