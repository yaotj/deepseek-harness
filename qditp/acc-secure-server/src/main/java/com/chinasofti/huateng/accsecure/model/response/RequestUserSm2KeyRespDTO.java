package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestUserSm2KeyRespDTO extends AccBizBaseResponse {

    private String privatePrivateKey;


    private String publicKey;


    private String sm2KeyPair;

    public String getPrivatePrivateKey() {
        return privatePrivateKey;
    }

    public void setPrivatePrivateKey(String privatePrivateKey) {
        this.privatePrivateKey = privatePrivateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSm2KeyPair() {
        return sm2KeyPair;
    }

    public void setSm2KeyPair(String sm2KeyPair) {
        this.sm2KeyPair = sm2KeyPair;
    }
}
