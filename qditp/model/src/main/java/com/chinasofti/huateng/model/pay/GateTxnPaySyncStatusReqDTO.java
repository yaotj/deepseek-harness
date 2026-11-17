package com.chinasofti.huateng.model.pay;


public class GateTxnPaySyncStatusReqDTO {

    private String orderNo;

    private String payStatus;

    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return "GateTxnPaySyncStatusReqDTO{orderNo='" + orderNo + "', payStatus='" + payStatus
                + "', remark='" + remark + "'}";
    }
}
