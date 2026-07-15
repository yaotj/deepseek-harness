package com.chinasofti.huateng.model.security;

/**
 * 请求导出用户私钥应答。
 *
 * <p>该对象解析 acc-security-server 返回 data 中用 ACC 侧 3DES 密钥保护的用户私钥。</p>
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
