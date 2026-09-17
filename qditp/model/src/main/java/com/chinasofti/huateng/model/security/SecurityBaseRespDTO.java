package com.chinasofti.huateng.model.security;

/**
 * acc-security-server通用应答字段。
 */
public class SecurityBaseRespDTO {
    /**
     * 返回码，200表示 acc-security-server 调用成功。
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
