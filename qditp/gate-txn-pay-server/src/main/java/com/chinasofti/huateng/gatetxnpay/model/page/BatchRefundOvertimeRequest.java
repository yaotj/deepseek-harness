package com.chinasofti.huateng.gatetxnpay.model.page;

import java.util.List;

public class BatchRefundOvertimeRequest {
    public static final int MAX_BATCH_SIZE = 200;

    private List<String> orderNos;
    private String refundReason;

    public List<String> getOrderNos() {
        return orderNos;
    }

    public void setOrderNos(List<String> orderNos) {
        this.orderNos = orderNos;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
