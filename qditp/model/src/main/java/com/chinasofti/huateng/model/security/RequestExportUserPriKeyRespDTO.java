package com.chinasofti.huateng.model.security;

/**
 * 请求导出用户私钥应答。
 */
public class RequestExportUserPriKeyRespDTO extends SecurityBaseRespDTO {
    /**
     * ACC侧3DES密钥加密的用户私钥。
     */
    private String userPrivateKeyByKes;

    public String getUserPrivateKeyByKes() {
        return userPrivateKeyByKes;
    }

    public void setUserPrivateKeyByKes(String userPrivateKeyByKes) {
        this.userPrivateKeyByKes = userPrivateKeyByKes;
    }
}
