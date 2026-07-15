package com.chinasofti.huateng.model.app;

/**
 * 查询黑名单应答参数。
 */
public class QueryBlackListResult {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 是否在黑名单，0否，1是。
     */
    private String inBlack;

    /**
     * 失败次数。
     */
    private Integer failedCount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getInBlack() {
        return inBlack;
    }

    public void setInBlack(String inBlack) {
        this.inBlack = inBlack;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }
}
