package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 按日票票号查购票支付信息的请求（服务间内部 DTO，非 APP 对外契约）。
 */
public class QueryDailyTicketPayInfoReqDTO {

    /** 日票票号，取自 {@code GATE_TXN_PAY.TICKET_CODE} */
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
