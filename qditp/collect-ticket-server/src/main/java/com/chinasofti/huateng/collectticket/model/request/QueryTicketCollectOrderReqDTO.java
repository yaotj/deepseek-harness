package com.chinasofti.huateng.collectticket.model.request;


public class QueryTicketCollectOrderReqDTO {

    private String orderNo;


    private String userId;


    private String qrcodeGenDate;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    @Override
    public String toString() {
        return "QueryTicketCollectOrderReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", qrcodeGenDate='" + qrcodeGenDate + '\'' +
                '}';
    }
}
