package com.chinasofti.huateng.model.app.dailyticket;


public class QueryDailyTicketPayInfoReqDTO {

    private String ticketCode;

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    @Override
    public String toString() {
        return "QueryDailyTicketPayInfoReqDTO{ticketCode='" + ticketCode + "'}";
    }
}
