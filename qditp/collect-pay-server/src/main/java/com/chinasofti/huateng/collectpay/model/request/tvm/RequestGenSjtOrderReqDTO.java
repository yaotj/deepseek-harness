package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF2A-01 提交单程票订单请求报文（TVM -> ITP）。
 */
public class RequestGenSjtOrderReqDTO extends BaseRequestDTO {
    /**
     * 起点站点代码。
     */
    private String entryStationCode;

    /**
     * 终点站点代码。
     */
    private String exitStationCode;

    /**
     * 票价（单位：分）。
     */
    private String ticketPrice;

    /**
     * 购买数量。
     */
    private String singelTicketNum;

    /**
     * 购票类型：0-按站点购票，1-按固定票价购票。
     */
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
        return super.toString()+","+ "RequestGenSjtOrderReqDTO{" +
                "entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice='" + ticketPrice + '\'' +
                ", singelTicketNum='" + singelTicketNum + '\'' +
                ", singleTicketType='" + singleTicketType + '\'' +
                ", payType='" + payType + '\'' +
                '}';
    }
}
