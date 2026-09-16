package com.chinasofti.huateng.model.pay;

/**
 * 支付结果回调驱动的扣费状态同步请求。
 *
 * <p>pay-sign-server 收到支付中心的支付结果回调、本地 PAY_TXN_DETAIL 落地成功后，
 * 用本报文通知 gate-txn-pay-server 把 GATE_TXN_PAY.DEBIT_STATUS 收敛到终态。</p>
 *
 * <p>存在的原因：GATE_TXN_PAY 只在「日票 / 零元交易」这一条分支上直接写 SUCCESS，
 * 真实免密扣款订单在调 pay-sign 后只会停在 PROCESSING 或 RETRY，
 * 没有任何代码能把它推进到 SUCCESS——扣款成功与否只有支付中心的回调知道，
 * 而回调只发到 pay-sign-server（2026-08-26 生产实测：全部真实扣款订单卡在中间态）。</p>
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
