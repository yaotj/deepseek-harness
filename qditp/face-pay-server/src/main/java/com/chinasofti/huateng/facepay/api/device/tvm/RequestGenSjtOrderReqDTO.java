package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestGenSjtOrderReqDTO extends BaseDeviceRequest {

    private String entryStationCode;

    private String exitStationCode;

    private String ticketPrice;

    private String singelTicketNum;

    private String singleTicketType;

    private String payType;

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public String getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(String ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(String singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestGenSjtOrderReqDTO{entryStationCode=" + entryStationCode
                + ", exitStationCode=" + exitStationCode
                + ", ticketPrice=" + ticketPrice
                + ", singelTicketNum=" + singelTicketNum
                + ", singleTicketType=" + singleTicketType
                + ", payType=" + payType
                + '}';
    }
}
