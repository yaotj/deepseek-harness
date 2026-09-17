package com.chinasofti.huateng.model.collectpay;

/**
 * 按订单号关闭 {@code TBL_TVM_APP_ORDER} 上待支付行的请求（{@code POST /internal/app-order/close-unpaid}）。
 */
public class AppPayOrderCloseReqDTO {

    /** 订单号。 */
    private String orderNo;

    private String msg;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getMsg() { return msg; }

    public void setMsg(String msg) { this.msg = msg; }

    @Override
    public String toString() {
        return "AppPayOrderCloseReqDTO{orderNo='" + orderNo + "', msg='" + msg + "'}";
    }
}
