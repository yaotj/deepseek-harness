package com.chinasofti.huateng.model.fephandle;

import java.io.Serial;
import java.io.Serializable;

public class OrderResponseData implements Serializable {

    @Serial
    private static final long serialVersionUID = -779623876632975432L;

    private String orderId;
    private String orderType;
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
