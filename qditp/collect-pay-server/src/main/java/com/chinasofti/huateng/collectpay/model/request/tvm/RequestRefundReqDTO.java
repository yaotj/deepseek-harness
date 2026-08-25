package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

/**
 * 退款请求DTO。
 * 根据订单号发起退款。
 */
public class RequestRefundReqDTO extends BaseRequestDTO {

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 退款原因。
     */
    private String refundReason;

    private String refundAmt;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundAmt() {
        return refundAmt;
    }

    public void setRefundAmt(String refundAmt) {
        this.refundAmt = refundAmt;
    }
}
