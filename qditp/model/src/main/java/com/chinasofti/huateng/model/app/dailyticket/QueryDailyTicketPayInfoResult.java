package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 按日票票号查购票支付信息的应答（服务间内部 DTO，非 APP 对外契约）。
 */
public class QueryDailyTicketPayInfoResult extends DailyTicketBaseResult {

    /** 支付交易订单号，来源 {@code DAILY_TICKET_ORDER.TRADE_NO} */
    private String payTradeOrderNo;

    /**
     * 购票付款时间，{@code yyyyMMddHHmmss}。
     */
    private String payOrderNoDate;

    /** 支付通道编码（{@code 03}=支付宝等），来源 {@code DAILY_TICKET_ORDER.PAY_CHANNEL_CODE} */
    private String payChannelCode;

    public String getPayTradeOrderNo() {
        return payTradeOrderNo;
    }

    public void setPayTradeOrderNo(String payTradeOrderNo) {
        this.payTradeOrderNo = payTradeOrderNo;
    }

    public String getPayOrderNoDate() {
        return payOrderNoDate;
    }

    public void setPayOrderNoDate(String payOrderNoDate) {
        this.payOrderNoDate = payOrderNoDate;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }
}
