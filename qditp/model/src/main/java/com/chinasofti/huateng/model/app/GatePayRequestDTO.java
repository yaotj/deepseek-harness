package com.chinasofti.huateng.model.app;

/**
 * 过闸扣费请求支付参数。
 */
public class GatePayRequestDTO extends RequestPayReqDTO {
    @Override
    public String toString() {
        return "GatePayRequestDTO{" +
                ", discountFee=" + getDiscountFee() +
                ", discountInfo='" + getDiscountInfo() + '\'' +
                '}';
    }
}
