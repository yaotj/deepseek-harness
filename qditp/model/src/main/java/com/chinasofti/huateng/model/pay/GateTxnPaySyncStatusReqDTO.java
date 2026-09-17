package com.chinasofti.huateng.model.pay;

/**
 * 支付结果回调驱动的扣费状态同步请求。
 */
public class GateTxnPaySyncStatusReqDTO {

    /** 过闸扣费订单号，即 PAY_TXN_DETAIL.ORDER_NO。 */
    private String orderNo;

    /** 支付结果，取值同 PAY_TXN_DETAIL.PAY_STATUS：SUCCESS / FAIL / CLOSED 等。 */
    private String payStatus;

    /** 备注，回写到 GATE_TXN_PAY.REMARK，便于人工追溯收敛来源。 */
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
