package com.chinasofti.huateng.collectpay.model.request.tvm;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;

public class RequestPayResultReqDTO extends BaseRequestDTO {
    /** 订单号。 */
    private String orderNo;

    private String userId;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
