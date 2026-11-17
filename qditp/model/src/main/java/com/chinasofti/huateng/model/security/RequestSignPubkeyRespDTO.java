package com.chinasofti.huateng.model.security;


public class RequestSignPubkeyRespDTO extends SecurityBaseRespDTO {

    private String signData;

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }
}
