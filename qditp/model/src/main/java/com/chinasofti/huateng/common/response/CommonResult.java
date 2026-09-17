package com.chinasofti.huateng.common.response;

/**
 * @date 2026/5/25 11:34。
 * @author zzm。
 */
public class CommonResult {

    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

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
}
