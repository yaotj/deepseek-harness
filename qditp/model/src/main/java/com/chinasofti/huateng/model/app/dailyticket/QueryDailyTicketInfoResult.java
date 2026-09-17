package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 查询日票信息响应。
 */
public class QueryDailyTicketInfoResult extends DailyTicketBaseResult {
    /** 日票票号。 */
    private String ticketCode;
    /** 计次票实际可用次数。 */
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
