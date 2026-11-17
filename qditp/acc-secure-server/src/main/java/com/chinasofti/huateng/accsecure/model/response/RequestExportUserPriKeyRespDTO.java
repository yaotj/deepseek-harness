package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestExportUserPriKeyRespDTO extends AccBizBaseResponse {

    private String userPrivateKeyByKes;

    public String getUserPrivateKeyByKes() {
        return userPrivateKeyByKes;
    }

    public void setUserPrivateKeyByKes(String userPrivateKeyByKes) {
        this.userPrivateKeyByKes = userPrivateKeyByKes;
    }
}
