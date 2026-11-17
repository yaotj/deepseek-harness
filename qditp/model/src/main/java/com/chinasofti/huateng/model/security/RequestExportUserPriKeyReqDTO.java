package com.chinasofti.huateng.model.security;


public class RequestExportUserPriKeyReqDTO {

    private String privateKey;


    private String publicKey;


    private String kekIdx;

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getKekIdx() {
        return kekIdx;
    }

    public void setKekIdx(String kekIdx) {
        this.kekIdx = kekIdx;
    }
}
