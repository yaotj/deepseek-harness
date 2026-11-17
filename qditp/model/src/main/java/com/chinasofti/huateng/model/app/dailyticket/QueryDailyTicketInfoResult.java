package com.chinasofti.huateng.model.app.dailyticket;


public class QueryDailyTicketInfoResult extends DailyTicketBaseResult {
    private String ticketCode;
    private Integer actualTimes;

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public Integer getActualTimes() {
        return actualTimes;
    }

    public void setActualTimes(Integer actualTimes) {
        this.actualTimes = actualTimes;
    }
}
