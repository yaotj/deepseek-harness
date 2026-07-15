package com.chinasofti.huateng.acc.security.server.itp.model;

public class ItpUserSm2KeyResponse{
    private String privateKey;
    private String publicKey;
    private String sm2KeyPair;

    public ItpUserSm2KeyResponse() {
    }

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

    public String getSm2KeyPair() {
        return sm2KeyPair;
    }

    public void setSm2KeyPair(String sm2KeyPair) {
        this.sm2KeyPair = sm2KeyPair;
    }
}
