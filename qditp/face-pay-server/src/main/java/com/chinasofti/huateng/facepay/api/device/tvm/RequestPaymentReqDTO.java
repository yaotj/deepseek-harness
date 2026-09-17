package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-02 付款码支付（主扫）请求报文。 */
public class RequestPaymentReqDTO extends BaseDeviceRequest {

    /** 订单号。 */
    private String orderNo;

    /** 乘客付款码（条码/二维码内容）。 */
    private String paymentCode;

    /** 支付渠道（微信/支付宝等）。 */
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

    /** {@code toString()} 里 NEVER 打印 paymentCode——付款码等同于支付凭证。 */
    @Override
    public String toString() {
        return super.toString() + ",RequestPaymentReqDTO{orderNo=" + orderNo
                + ", paymentCode=***"
                + ", paymentVendor=" + paymentVendor + '}';
    }
}
