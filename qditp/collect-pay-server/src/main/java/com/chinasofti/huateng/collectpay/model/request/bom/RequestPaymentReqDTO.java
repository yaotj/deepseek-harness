package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * IF8A-05 扫码支付请求DTO。
 * BOM扫描用户支付客户端的付款码后，向ITP平台发起支付请求时使用的业务参数。
 */
public class RequestPaymentReqDTO extends BaseRequestDTO {

    /**
     * 订单号。
     * 由ITP平台在非现金收款下单时生成并返回。
     */
    private String orderNo;

    /**
     * 支付通道编码。
     */
    private String paymentCode;

    /**
     * 支付账户认证码。
     * 即用户付款码信息。
     */
    private String paymentVendor;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    @Override
    public String toString() {
        return "RequestPaymentReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", paymentCode='" + paymentCode + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                ", providerId='" + getProviderId() + '\'' +
                ", deviceId='" + getDeviceId() + '\'' +
                '}';
    }
}