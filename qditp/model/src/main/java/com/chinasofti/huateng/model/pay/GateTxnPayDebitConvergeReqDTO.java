package com.chinasofti.huateng.model.pay;

/**
 * 补款支付成功后请求 gate-txn-pay 收敛原过闸订单扣费状态。
 */
public class GateTxnPayDebitConvergeReqDTO {

    /**
     * 原过闸扣费订单号，即 {@code GATE_TXN_PAY.ORDER_NO}，也是补款明细里的 {@code ORIG_ORDER_NO}。
     */
    private String origOrderNo;

    /**
     * 原订单交易日期 {@code GATE_TXN_PAY.TXN_DATE}（{@code yyyyMMdd}）。
     */
    private String txnDate;

    /**
     * 回写到 {@code GATE_TXN_PAY.REMARK} 的收敛来源，便于人工追溯是哪张补款单结清的。
     */
    private String remark;

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public String toString() {
        return "GateTxnPayDebitConvergeReqDTO{origOrderNo='" + origOrderNo
                + "', txnDate='" + txnDate + "', remark='" + remark + "'}";
    }
}
