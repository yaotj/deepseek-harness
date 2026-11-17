package com.chinasofti.huateng.model.security;


public class RequestExportUserPriKeyRespDTO extends SecurityBaseRespDTO {

    private String userPrivateKeyByKes;

    public String getUserPrivateKeyByKes() {
        return userPrivateKeyByKes;
    }

    public void setUserPrivateKeyByKes(String userPrivateKeyByKes) {
        this.userPrivateKeyByKes = userPrivateKeyByKes;
    }
}
