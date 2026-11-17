package com.chinasofti.huateng.model.pay;


public class SupplementOrderRespDTO {
    private String retCode;
    private String retMsg;
    private String orderNo;
    private String payStatus;
    private Long totalAmount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    @Override
    public String toString() {
        return "SupplementOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", payStatus='" + payStatus + '\'' +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
