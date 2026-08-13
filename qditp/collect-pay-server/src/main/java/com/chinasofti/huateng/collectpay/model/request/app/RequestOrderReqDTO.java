package com.chinasofti.huateng.collectpay.model.request.app;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/**
 * IF8A-20 请求下单请求参数DTO。
 * APP_SERVER向ITP平台发起下单请求的参数封装。
 */
@Data
public class RequestOrderReqDTO extends BaseRequestDTO {

    /**
     * 用户编码。
     */
    private String userId;

    /**
     * 起点站点代码。
     */
    private String entryStationCode;

    /**
     * 终点站点代码。
     */
    private String exitStationCode;

    /**
     * 票价，单位：分。
     */
    private String ticketPrice;

    /**
     * 购买数量。
     */
    private String singelTicketNum;

    /**
     * 购票类型。
     * 0-有起点站和终点站。
     */
    private String singleTicketType;

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
        return "RequestOrderReqDTO{" +
                "userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice='" + ticketPrice + '\'' +
                ", singelTicketNum='" + singelTicketNum + '\'' +
                ", singleTicketType='" + singleTicketType + '\'' +
                '}';
    }
}