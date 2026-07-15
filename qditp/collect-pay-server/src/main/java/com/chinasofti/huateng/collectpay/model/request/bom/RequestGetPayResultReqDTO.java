package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF8A-06 查询支付结果请求DTO。
 * BOM支付超时或轮询查询支付状态时使用的业务参数。
 */
public class RequestGetPayResultReqDTO extends BaseRequestDTO {

    /**
     * 订单号。
     * 需要查询支付结果的订单编号。
     */
    private String orderNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    @Override
    public String toString() {
        return "RequestGetPayResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}