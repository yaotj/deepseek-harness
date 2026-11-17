package com.chinasofti.huateng.model.app;


public class RequestAgreeReleaseReqDTO {
    private String agreementCode;

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    @Override
    public String toString() {
        return "RequestAgreeReleaseReqDTO{agreementCode='" + agreementCode + "'}";
    }
}
