package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripTerminateContractReqDTO {


    private String agreementCode;


    private String merchantNo;

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }
}
