package com.chinasofti.huateng.model.app;

/**
 * IF8A-36 请求移除签约信息请求参数。
 */
public class RequestAgreeReleaseReqDTO {
    /** 协议编码，签约时生成的唯一协议编号。 */
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
