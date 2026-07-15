package com.chinasofti.huateng.model.security;

/**
 * 请求签名用户公钥参数。
 *
 * <p>该对象用于 key-server 调用 acc-security-server 的 /ci/itp/requestSignPubkey。</p>
 */
public class RequestSignPubkeyReqDTO {
    /**
     * 用户SM2公钥X分量，64位HEX。
     */
    private String publicKeyX;

    /**
     * 用户ID，四字节HEX。
     */
    private String userId;

    /**
     * 用户公钥有效期，四字节HEX。
     */
    private String publicKeyEffectiveDate;

    /**
     * LMK加密的CA私钥，来自 METRO_CA_KEYSTORE.KEY_PRIVATE。
     */
    private String caPrivateKey;

    /**
     * CA公钥XY，来自 METRO_CA_KEYSTORE.KEY_PUBLIC。
     */
    private String caPublicKey;

    /**
     * CA SM2密钥密文对，来自 METRO_CA_KEYSTORE.KEY_PAIR。
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
