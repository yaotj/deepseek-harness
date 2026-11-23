package com.chinasofti.huateng.heartbeat.model;

/**
 * 设备心跳响应DTO
 */
public class HeartbeatResponse {
    private String retCode;
    private String retMsg;

    public static HeartbeatResponse success() {
        HeartbeatResponse resp = new HeartbeatResponse();
        resp.retCode = "0000";
        resp.retMsg = "成功";
        return resp;
    }

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
