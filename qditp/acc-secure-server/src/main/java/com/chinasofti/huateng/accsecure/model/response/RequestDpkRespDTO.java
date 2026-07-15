package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;

/**
 * IF7B-07 请求HCE卡片消费密钥响应报文。
 */
public class RequestDpkRespDTO extends AccBizBaseResponse {
    /**
     * KEK保护的用户消费子密钥。
     */
    private String dpkByKek;

    public String getDpkByKek() {
        return dpkByKek;
    }

    public void setDpkByKek(String dpkByKek) {
        this.dpkByKek = dpkByKek;
    }
}
