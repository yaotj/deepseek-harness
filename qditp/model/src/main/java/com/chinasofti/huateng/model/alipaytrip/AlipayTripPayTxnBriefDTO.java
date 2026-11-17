package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripPayTxnBriefDTO {

    private String orderNo;

    private String payStatus;

    private String channelOrderNo;

    private String transTime;

    private String invoice;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    @Override
    public String toString() {
        return "AlipayTripPayTxnBriefDTO{orderNo='" + orderNo + "', payStatus='" + payStatus
                + "', channelOrderNo='" + channelOrderNo + "', transTime='" + transTime
                + "', invoice='" + invoice + "'}";
    }
}
