package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** APP 侧「按订单号 + 用户号」的通用入参：支付结果查询、请求退款、退款结果查询三个接口共用。 */
public class RequestAppPayResultReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String userId;

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

    @Override
    public String toString() {
        return "RequestAppPayResultReqDTO{orderNo=" + orderNo + ", userId=" + userId + '}';
    }
}
