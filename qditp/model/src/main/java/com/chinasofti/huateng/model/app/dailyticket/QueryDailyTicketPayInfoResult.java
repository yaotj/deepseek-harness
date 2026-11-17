package com.chinasofti.huateng.model.app.dailyticket;


public class QueryDailyTicketPayInfoResult extends DailyTicketBaseResult {

    private String payTradeOrderNo;


    private String payOrderNoDate;

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
