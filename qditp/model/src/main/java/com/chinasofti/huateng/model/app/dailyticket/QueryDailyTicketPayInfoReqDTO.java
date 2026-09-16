package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 按日票票号查购票支付信息的请求（服务间内部 DTO，非 APP 对外契约）。
 *
 * <p>用途：IF8A-05 / IF8A-34 的日票免扣费单在 {@code PAY_TXN_DETAIL} 里**没有行**
 * （那趟行程不扣费，钱是买日票时付的），于是 {@code payTradeOrderNo} / {@code payOrderNoDate} /
 * {@code payChannelCode} 三个字段只能返空串。本请求把 {@code GATE_TXN_PAY.TICKET_CODE}
 * 交给日票域换回购票时的支付信息，用来填那三个字段。</p>
 *
 * <p><b>入参只用 {@code ticketCode}，NEVER 改成 {@code cardNum}</b>：一张卡可以先后买过多张日票，
 * 按卡号查会命中历史多单、取到错误的那笔支付信息；{@code ticketCode} 才唯一对应
 * 「这趟行程用的那张票」（{@code DAILY_TICKET_INSTANCE.TICKET_CODE}）。</p>
 *
 * <p><b>NEVER 把本类与 {@code QueryDailyTicketInfoReqDTO} 合并</b>：后者服务 IF1A-01 闸机热路径
 * （{@code GateDailyTicketCoordinator} 取 countingFlag / countingTimes），入参是
 * {@code orderNo} / {@code cardId}，语义与调用时机都不同；合并会让热路径为查询链路的需求买单。</p>
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
