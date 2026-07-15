package com.chinasofti.huateng.model.security;

/**
 * 请求生成用户SM2密钥对应答。
 *
 * <p>该对象解析 acc-security-server 返回 data 中的用户密钥材料。</p>
 */
public class RequestUserSm2KeyRespDTO extends SecurityBaseRespDTO {
    /**
     * LMK加密的用户SM2私钥。
     */
    private String privateKey;

    /**
     * 用户SM2公钥XY。
     */
    private String publicKey;

    /**
     * 用户SM2密钥密文对，兼容部分ACC环境返回格式。
     */
    private String sm2KeyPair;

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
