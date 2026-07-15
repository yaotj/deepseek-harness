package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;

/**
 * IF7B-04 请求签名用户公钥响应报文。
 */
public class RequestSignPubkeyRespDTO extends AccBizBaseResponse {
    /**
     * 用户公钥签名数据。
     */
    private String signData;

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }
}
