package com.chinasofti.huateng.model.security;

/**
 * 请求签名用户公钥应答。
 */
public class RequestSignPubkeyRespDTO extends SecurityBaseRespDTO {
    /**
     * CA对用户公钥相关数据的签名结果，返回给 APP 的 keyList.signData。
     */
    private String signData;

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }
}
