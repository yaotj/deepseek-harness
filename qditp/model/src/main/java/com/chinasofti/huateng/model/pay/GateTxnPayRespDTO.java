package com.chinasofti.huateng.model.pay;

/**
 * 过闸扣费交易处理响应。
 */
public class GateTxnPayRespDTO {
    /** 返回码，0000 表示 gate-txn-pay-server 已接收并处理。 */
    private String retCode;
    /** 返回消息。 */
    private String retMsg;
    /** 地铁侧订单号，同时作为支付接口 orderNo。 */
    private String orderNo;
    /** 本地支付状态，如 PROCESSING、RETRY。 */
    private String payStatus;

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
}
