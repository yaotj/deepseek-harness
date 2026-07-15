package com.chinasofti.huateng.accsecure.model.response;

import com.chinasofti.huateng.accsecure.model.common.AccBizBaseResponse;

/**
 * IF7B-08 请求发售HCE单程票响应报文。
 */
public class RequestHecCardDateRespDTO extends AccBizBaseResponse {
    /**
     * 单程票数据发行发售内容。
     */
    private String hecData;

    /**
     * 用户逻辑卡号。
     */
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
