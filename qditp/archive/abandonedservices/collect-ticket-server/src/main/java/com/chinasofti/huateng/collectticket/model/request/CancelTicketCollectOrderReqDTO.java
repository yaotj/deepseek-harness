package com.chinasofti.huateng.collectticket.model.request;

/**
 * IF2A-05 取消取票订单请求报文。
 */
public class CancelTicketCollectOrderReqDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 取消原因。
     */
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
