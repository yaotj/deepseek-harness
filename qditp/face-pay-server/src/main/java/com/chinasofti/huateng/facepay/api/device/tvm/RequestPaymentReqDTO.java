package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-02 付款码支付（主扫）请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestPayment} 的 {@code bizData}。
 *
 * <p><b>本接口的错误码族与其余 TVM 接口不同</b>：旧实现的校验失败走
 * {@code BomOrderResult.failMessage(...)}，{@code retCode} 是 <b>8999</b> 而不是 2002/2999。
 * 这是既有契约（TVM 与 BOM 共用同一段实现），重写照搬，见
 * {@code api/device/bom/BomResponses}。</p>
 */
public class RequestPaymentReqDTO extends BaseDeviceRequest {

    /** 订单号。必填，为空时返回 retCode=8999「orderNo不能为空」。 */
    private String orderNo;

    /** 乘客付款码（条码/二维码内容）。旧实现不校验但会原样送支付中心。 */
    private String paymentCode;

    /** 支付渠道（微信/支付宝等）。必填，为空时返回 retCode=8999「paymentVendor不能为空」。 */
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

    /** {@code toString()} 里 <b>NEVER 打印 paymentCode</b>——付款码等同于支付凭证。 */
    @Override
    public String toString() {
        return super.toString() + ",RequestPaymentReqDTO{orderNo=" + orderNo
                + ", paymentCode=***"
                + ", paymentVendor=" + paymentVendor + '}';
    }
}
