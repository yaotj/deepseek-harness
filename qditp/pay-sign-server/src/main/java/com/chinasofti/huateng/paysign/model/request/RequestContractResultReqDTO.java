package com.chinasofti.huateng.paysign.model.request;

public class RequestContractResultReqDTO {
    private String thirdUserId;
    private String requestSignSeq;
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
