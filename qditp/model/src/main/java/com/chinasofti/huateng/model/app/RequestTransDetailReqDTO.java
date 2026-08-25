package com.chinasofti.huateng.model.app;

/**
 * IF8A-34 获取订单详情请求参数。
 */
public class RequestTransDetailReqDTO {
    private String thirdUserId;
    private String orderNo;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    @Override
    public String toString() {
        return "RequestTransDetailReqDTO{thirdUserId='" + thirdUserId + "', orderNo='" + orderNo + "'}";
    }
}
