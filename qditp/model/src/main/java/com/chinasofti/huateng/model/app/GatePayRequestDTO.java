package com.chinasofti.huateng.model.app;


public class GatePayRequestDTO extends RequestPayReqDTO {
    @Override
    public String toString() {
        return "GatePayRequestDTO{" +
                ", discountFee=" + getDiscountFee() +
                ", discountInfo='" + getDiscountInfo() + '\'' +
                '}';
    }
}
