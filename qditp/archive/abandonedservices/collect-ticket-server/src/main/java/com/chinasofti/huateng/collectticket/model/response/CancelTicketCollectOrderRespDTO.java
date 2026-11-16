package com.chinasofti.huateng.collectticket.model.response;

/**
 * IF2A-05 取消取票订单响应报文。
 */
public class CancelTicketCollectOrderRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 订单状态。
     */
    private Integer orderStatus;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    @Override
    public String toString() {
        return "CancelTicketCollectOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", orderStatus=" + orderStatus +
                '}';
    }
}
