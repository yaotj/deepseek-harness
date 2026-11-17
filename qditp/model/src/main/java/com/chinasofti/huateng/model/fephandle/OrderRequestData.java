package com.chinasofti.huateng.model.fephandle;

import java.io.Serial;
import java.io.Serializable;

public class OrderRequestData implements Serializable {

    @Serial
    private static final long serialVersionUID = -1423708039822294309L;

    private String orderId;
    private String orderType;
    private Object orderData;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Object getOrderData() {
        return orderData;
    }

    public void setOrderData(Object orderData) {
        this.orderData = orderData;
    }
}
