package com.chinasofti.huateng.collectpay.model.response.tvm;

/**
 * IF2A-03 查询支付结果应答报文（ITP -> TVM）。
 */
public class RequestPayResultRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 支付通道编码。
     */
    private String paymentChannelCode;

    /**
     * 支付结果。
     * ORDERED-已经下单，SUCCESS-成功，FAILED-失败。
     */
    private String paymentResult;

    /**
     * 支付结果描述。
     */
    private String paymentResultDesc;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getPaymentChannelCode() {
        return paymentChannelCode;
    }

    public void setPaymentChannelCode(String paymentChannelCode) {
        this.paymentChannelCode = paymentChannelCode;
    }

    public String getPaymentResult() {
        return paymentResult;
    }

    public void setPaymentResult(String paymentResult) {
        this.paymentResult = paymentResult;
    }

    public String getPaymentResultDesc() {
        return paymentResultDesc;
    }

    public void setPaymentResultDesc(String paymentResultDesc) {
        this.paymentResultDesc = paymentResultDesc;
    }

    @Override
    public String toString() {
        return "RequestPayResultRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", paymentChannelCode='" + paymentChannelCode + '\'' +
                ", paymentResult='" + paymentResult + '\'' +
                ", paymentResultDesc='" + paymentResultDesc + '\'' +
                '}';
    }
}
