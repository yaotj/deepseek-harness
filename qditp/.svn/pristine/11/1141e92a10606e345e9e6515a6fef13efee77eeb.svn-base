package com.chinasofti.huateng.collectpay.model.request.app;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/**
 * IF8A-11 请求支付信息请求参数DTO。
 * APP_SERVER向ITP平台发起支付请求的参数封装。
 */
@Data
public class RequestPayInfoReqDTO extends BaseRequestDTO {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 支付通道编码。
     * 10001-支付宝SDK
     * 10002-微信支付SDK
     * 10003-联支付SDK
     */
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
        return "RequestPayInfoReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", payChannelCode='" + payChannelCode + '\'' +
                '}';
    }
}