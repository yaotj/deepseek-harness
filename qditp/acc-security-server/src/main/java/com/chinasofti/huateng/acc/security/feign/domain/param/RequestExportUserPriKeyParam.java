package com.chinasofti.huateng.acc.security.feign.domain.param;

/**
 * @author zzm
 * @date 2026/5/14 16:56
 */
public class RequestExportUserPriKeyParam {

    /**
     * LMK加密的私钥k
     */
    private String privateKey;


    /**
     * 公钥XY
     */
    private String publicKey;

    /**
     * kek密钥索引
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
