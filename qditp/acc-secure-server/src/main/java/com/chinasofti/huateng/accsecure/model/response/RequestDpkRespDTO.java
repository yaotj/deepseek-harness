package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestDpkRespDTO extends AccBizBaseResponse {

    private String dpkByKek;

    public String getDpkByKek() {
        return dpkByKek;
    }

    public void setDpkByKek(String dpkByKek) {
        this.dpkByKek = dpkByKek;
    }
}
