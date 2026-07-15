package com.chinasofti.huateng.wallet.model.sign;

/**
 * IF8A-16 请求签约请求信息响应报文。
 */
public class RequestSignInfoRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 调用 SDK 所需的请求参数。
     */
    private String requestStartSdkInfo;

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

    public String getRequestStartSdkInfo() {
        return requestStartSdkInfo;
    }

    public void setRequestStartSdkInfo(String requestStartSdkInfo) {
        this.requestStartSdkInfo = requestStartSdkInfo;
    }
}
