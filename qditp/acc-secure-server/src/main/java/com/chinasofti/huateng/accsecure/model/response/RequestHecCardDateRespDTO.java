package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;


public class RequestHecCardDateRespDTO extends AccBizBaseResponse {

    private String hecData;


    private String logicNum;

    public String getHecData() {
        return hecData;
    }

    public void setHecData(String hecData) {
        this.hecData = hecData;
    }

    public String getLogicNum() {
        return logicNum;
    }

    public void setLogicNum(String logicNum) {
        this.logicNum = logicNum;
    }
}
