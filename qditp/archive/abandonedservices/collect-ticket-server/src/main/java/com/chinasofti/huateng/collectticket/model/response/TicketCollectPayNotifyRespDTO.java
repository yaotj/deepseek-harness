package com.chinasofti.huateng.collectticket.model.response;

/**
 * 支付中心回调取票订单支付结果响应报文。
 */
public class TicketCollectPayNotifyRespDTO {
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

    @Override
    public String toString() {
        return "TicketCollectPayNotifyRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                '}';
    }
}
