package com.chinasofti.huateng.collectticket.model.request;

/**
 * IF8A-20 请求下单请求报文。
 * 当前 DTO 尚未完全覆盖接口文档字段。
 */
public class CreateTicketCollectOrderReqDTO {
    /**
     * 用户 ID。
     */
    private String userId;

    /**
     * 起点站点编码。
     */
    private String entryStationCode;

    /**
     * 终点站点编码。
     */
    private String exitStationCode;

    /**
     * 单程票数量。
     */
    private Integer singelTicketNum;

    /**
     * 票价，单位分。
     * 接口文档 IF8A-20 中定义了该字段，当前服务实现尚未使用。
     */
    private Integer ticketPrice;

    /**
     * 单程票类型。
     * 接口文档 IF8A-20 中定义了该字段，当前服务实现固定按 0 处理。
     */
    private Integer singleTicketType;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 渠道类型，1-APP，2-ETC。
     */
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
