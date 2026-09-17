package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** 单程票交易查询入参（BOM 侧 {@code requestOrderResult}）。 */
public class RequestOrderResultReqDTO extends BaseDeviceRequest {

    private String ticketLogicNum;

    private String transDate;

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    @Override
    public String toString() {
        return "RequestOrderResultReqDTO{ticketLogicNum=" + ticketLogicNum
                + ", transDate=" + transDate
                + ", deviceId=" + getDeviceId() + '}';
    }
}
