package com.chinasofti.huateng.model.security;

/**
 * 安全服务 {@code /ci/itp/requestHceCardData} 的响应数据。
 */
public class RequestHceCardDataRespDTO extends SecurityBaseRespDTO {
    /**
     * HCE 卡数据。
     */
    private String hceData;

    /**
     * HCE 逻辑卡号，account-server 将此值作为开户结果的 {@code cardId} 返回 APP。
     */
    private String logicNum;

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    public String getLogicNum() {
        return logicNum;
    }

    public void setLogicNum(String logicNum) {
        this.logicNum = logicNum;
    }
}
