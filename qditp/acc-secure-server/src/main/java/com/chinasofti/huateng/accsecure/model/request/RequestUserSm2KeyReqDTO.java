package com.chinasofti.huateng.accsecure.model.request;

/**
 * IF7B-03 请求生成用户SM2密钥请求报文。
 */
public class RequestUserSm2KeyReqDTO {
    /**
     * 用户逻辑卡号。
     */
    private String logicNum;

    public String getLogicNum() {
        return logicNum;
    }

    public void setLogicNum(String logicNum) {
        this.logicNum = logicNum;
    }
}
