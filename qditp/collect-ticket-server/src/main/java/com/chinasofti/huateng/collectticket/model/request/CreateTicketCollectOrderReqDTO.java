package com.chinasofti.huateng.collectticket.model.request;


public class CreateTicketCollectOrderReqDTO {

    private String userId;


    private String entryStationCode;


    private String exitStationCode;


    private Integer singelTicketNum;


    private Integer ticketPrice;


    private Integer singleTicketType;


    private String channelCode;


    private String channelType;

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

    public Integer getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(Integer singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(Integer singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    @Override
    public String toString() {
        return "CreateTicketCollectOrderReqDTO{" +
                "userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", singelTicketNum=" + singelTicketNum +
                ", ticketPrice=" + ticketPrice +
                ", singleTicketType=" + singleTicketType +
                ", channelCode='" + channelCode + '\'' +
                ", channelType='" + channelType + '\'' +
                '}';
    }
}
