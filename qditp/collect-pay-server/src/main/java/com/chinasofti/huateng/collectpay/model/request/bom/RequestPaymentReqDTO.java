package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/** IF8A-05 扫码支付请求DTO。 */
public class RequestPaymentReqDTO extends BaseRequestDTO {

    /** 订单号。 */
    private String orderNo;

    /** 支付通道编码。 */
    private String paymentCode;

    /** 支付账户认证码。 */
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