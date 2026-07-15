package com.chinasofti.huateng.acc.security.feign.domain.param;

/**
 * @author zzm
 * @date 2026/5/14 16:37
 */
public class RequestSignPubkeyParam {

    /**
     * 用户公钥X
     */
    private String publicKeyX;

    /**
     * 用户账户标识
     */
    private String userId;

    /**
     * 公钥有效期
     */
    private String publicKeyEffectiveDate;

    /**
     * LMK加密的CA私钥d
     */
    private String caPrivateKey;

    /**
     * CA公钥XY
     */
    private String caPublicKey;

    /**
     * SM2密钥密文对
     */
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
