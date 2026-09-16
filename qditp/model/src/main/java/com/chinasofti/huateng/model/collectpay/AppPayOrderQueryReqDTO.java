package com.chinasofti.huateng.model.collectpay;

/**
 * 按订单号回查 {@code TBL_TVM_APP_ORDER} 支付结果的请求（{@code POST /internal/app-order/pay-result}）。
 */
public class AppPayOrderQueryReqDTO {

    /** 订单号。 */
    private String orderNo;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    @Override
    public String toString() {
        return "AppPayOrderQueryReqDTO{orderNo='" + orderNo + "'}";
    }
}
