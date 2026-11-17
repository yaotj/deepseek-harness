package com.chinasofti.huateng.model.pay;


public class GateTxnPayDebitConvergeReqDTO {


    private String origOrderNo;


    private String txnDate;


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
