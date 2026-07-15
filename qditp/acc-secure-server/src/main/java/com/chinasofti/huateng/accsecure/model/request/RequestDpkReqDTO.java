package com.chinasofti.huateng.accsecure.model.request;

/**
 * IF7B-07 请求HCE卡片消费密钥请求报文。
 */
public class RequestDpkReqDTO {
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
