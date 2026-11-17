package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestSignPubkeyRespDTO extends AccBizBaseResponse {

    private String signData;

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }
}
