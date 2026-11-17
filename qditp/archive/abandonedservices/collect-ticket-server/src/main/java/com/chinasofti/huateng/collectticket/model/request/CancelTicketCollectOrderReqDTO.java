package com.chinasofti.huateng.collectticket.model.request;


public class CancelTicketCollectOrderReqDTO {

    private String orderNo;


    private String userId;


    private String cancelReason;

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

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    @Override
    public String toString() {
        return "CancelTicketCollectOrderReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", cancelReason='" + cancelReason + '\'' +
                '}';
    }
}
