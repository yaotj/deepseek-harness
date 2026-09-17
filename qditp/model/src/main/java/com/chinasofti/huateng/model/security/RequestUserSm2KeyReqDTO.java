package com.chinasofti.huateng.model.security;

/**
 * 请求生成用户SM2密钥对参数。
 */
public class RequestUserSm2KeyReqDTO {
    /**
     * 逻辑卡号。旧系统使用 cardId + cardId 作为入参。
     */
    private String logicNum;

    public String getLogicNum() {
        return logicNum;
    }

    public void setLogicNum(String logicNum) {
        this.logicNum = logicNum;
    }
}
