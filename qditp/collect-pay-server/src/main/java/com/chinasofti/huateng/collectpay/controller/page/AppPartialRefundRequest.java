package com.chinasofti.huateng.collectpay.controller.page;

/** 运营端 APP 取票订单「指定金额退款」请求体。 */
public class AppPartialRefundRequest {

    /** 本次退款金额，单位**分**，正整数。 */
    private Integer refundAmount;

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }
}
