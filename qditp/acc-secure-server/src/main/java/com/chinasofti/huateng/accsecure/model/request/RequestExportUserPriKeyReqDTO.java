package com.chinasofti.huateng.accsecure.model.request;


public class RequestExportUserPriKeyReqDTO {

    private String privatePrivateKey;


    private String kekIdx;

    public String getPrivatePrivateKey() {
        return privatePrivateKey;
    }

    public void setPrivatePrivateKey(String privatePrivateKey) {
        this.privatePrivateKey = privatePrivateKey;
    }

    public String getKekIdx() {
        return kekIdx;
    }

    public void setKekIdx(String kekIdx) {
        this.kekIdx = kekIdx;
    }
}
