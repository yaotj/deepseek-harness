package com.chinasofti.huateng.collectticket.model.request;

/**
 * IF2A-02 查询取票订单状态请求报文。
 */
public class QueryTicketCollectOrderReqDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 设备取票二维码生成时间。
     */
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
