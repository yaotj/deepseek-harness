package com.chinasofti.huateng.model.app;

import java.util.List;


public class QueryPayTxnBatchReqDTO {
    private List<String> orderNos;

    public List<String> getOrderNos() {
        return orderNos;
    }

    public void setOrderNos(List<String> orderNos) {
        this.orderNos = orderNos;
    }

    @Override
    public String toString() {
        return "QueryPayTxnBatchReqDTO{orderNos=" + orderNos + '}';
    }
}
