package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 日票接口通用响应。
 */
public class DailyTicketBaseResult {
    /**
     * 返回码，0000表示成功。
     */
    private String retCode;

    /**
     * 返回信息。
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
