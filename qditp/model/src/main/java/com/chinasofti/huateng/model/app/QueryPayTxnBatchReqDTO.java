package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * IF8A-05 批量查询支付明细请求参数。
 */
public class QueryPayTxnBatchReqDTO {
    /** 订单号列表 */
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
