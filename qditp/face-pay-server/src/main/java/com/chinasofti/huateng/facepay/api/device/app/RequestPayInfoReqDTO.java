package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF8A-11 请求支付信息入参。
 *
 * <p>{@code payChannelCode} 同时作为支付中心报文的 {@code paymentVendor}，
 * 旧实现把它在两个位置各传一次，本实现保持一致。</p>
 */
public class RequestPayInfoReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String payChannelCode;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    @Override
    public String toString() {
        return "RequestPayInfoReqDTO{orderNo=" + orderNo
                + ", payChannelCode=" + payChannelCode + '}';
    }
}
