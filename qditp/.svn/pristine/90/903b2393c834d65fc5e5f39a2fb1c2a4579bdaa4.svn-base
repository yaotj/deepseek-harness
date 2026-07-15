package com.chinasofti.huateng.model.security;

/**
 * 请求导出用户私钥参数。
 *
 * <p>该对象用于 key-server 调用 acc-security-server 的 /ci/itp/requestExportUserPriKey。</p>
 */
public class RequestExportUserPriKeyReqDTO {
    /**
     * LMK加密的用户SM2私钥。
     */
    private String privateKey;

    /**
     * 用户SM2公钥XY。
     */
    private String publicKey;

    /**
     * KEK密钥索引，配置项 key.sync.kek-idx。
     */
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
