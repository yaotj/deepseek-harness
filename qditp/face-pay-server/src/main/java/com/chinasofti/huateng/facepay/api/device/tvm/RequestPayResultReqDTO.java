package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-03 查询支付结果请求报文。 */
public class RequestPayResultReqDTO extends BaseDeviceRequest {

    /** 订单号。 */
    private String orderNo;

    /** 用户标识。 */
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
        return super.toString() + ",RequestPayResultReqDTO{orderNo=" + orderNo + ", userId=" + userId + '}';
    }
}
