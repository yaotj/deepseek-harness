package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestOrderReqDTO extends BaseDeviceRequest {

    private String userId;

    private String entryStationCode;

    private String exitStationCode;

    private String ticketPrice;

    private String singelTicketNum;

    private String singleTicketType;

    public Long priceInFen() {
        return parse(ticketPrice);
    }

    public Integer ticketCount() {
        Long value = parse(singelTicketNum);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    @Override
    public String toString() {
        return "RequestOrderReqDTO{userId=" + userId
                + ", entryStationCode=" + entryStationCode
                + ", exitStationCode=" + exitStationCode
                + ", ticketPrice=" + ticketPrice
                + ", singelTicketNum=" + singelTicketNum
                + ", singleTicketType=" + singleTicketType + '}';
    }
}
