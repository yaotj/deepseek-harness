package com.chinasofti.huateng.accsecure.model.request;

/**
 * IF7B-02 请求生成地铁CA密钥请求报文。
 */
public class RequestCaKeyReqDTO {
    /**
     * 密钥索引。
     */
    private String keyIdx;

    public String getKeyIdx() {
        return keyIdx;
    }

    public void setKeyIdx(String keyIdx) {
        this.keyIdx = keyIdx;
    }
}
