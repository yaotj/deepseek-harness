package com.chinasofti.huateng.model.pay;

/**
 * IF8A-26 请求补款下单应答报文。
 */
public class SupplementOrderRespDTO {
    /** 返回码，0000 表示补款单已生成。 */
    private String retCode;
    /** 返回消息。 */
    private String retMsg;
    /** 补款单号，SP 前缀，同时作为后续调支付接口的 orderNo。 */
    private String orderNo;
    /** 补款单支付状态：INIT / PROCESSING / SUCCESS / FAIL / CLOSED。 */
    private String payStatus;
    /** 补款单总金额，单位分。 */
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
