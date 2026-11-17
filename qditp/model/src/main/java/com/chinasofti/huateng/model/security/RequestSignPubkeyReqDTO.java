package com.chinasofti.huateng.model.security;


public class RequestSignPubkeyReqDTO {

    private String publicKeyX;


    private String userId;


    private String publicKeyEffectiveDate;


    private String caPrivateKey;


    private String caPublicKey;


    private String caSm2KeyPair;

    public String getPublicKeyX() {
        return publicKeyX;
    }

    public void setPublicKeyX(String publicKeyX) {
        this.publicKeyX = publicKeyX;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPublicKeyEffectiveDate() {
        return publicKeyEffectiveDate;
    }

    public void setPublicKeyEffectiveDate(String publicKeyEffectiveDate) {
        this.publicKeyEffectiveDate = publicKeyEffectiveDate;
    }

    public String getCaPrivateKey() {
        return caPrivateKey;
    }

    public void setCaPrivateKey(String caPrivateKey) {
        this.caPrivateKey = caPrivateKey;
    }

    public String getCaPublicKey() {
        return caPublicKey;
    }

    public void setCaPublicKey(String caPublicKey) {
        this.caPublicKey = caPublicKey;
    }

    public String getCaSm2KeyPair() {
        return caSm2KeyPair;
    }

    public void setCaSm2KeyPair(String caSm2KeyPair) {
        this.caSm2KeyPair = caSm2KeyPair;
    }
}
