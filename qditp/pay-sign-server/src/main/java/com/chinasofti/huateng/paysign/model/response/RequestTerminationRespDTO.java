package com.chinasofti.huateng.paysign.model.response;

public class RequestTerminationRespDTO extends BaseRespDTO {

    /** 商户端签约流水号（复用原签约流水号）。 */
    private String requestSignSeq;

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }
}
