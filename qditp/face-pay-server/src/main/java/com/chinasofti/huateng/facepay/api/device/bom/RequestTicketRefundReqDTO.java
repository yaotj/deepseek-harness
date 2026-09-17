package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** 单程票退款入参（BOM 侧 {@code requestTicketRefund}）。 */
public class RequestTicketRefundReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String ticketLogicNum;

    /** 退款金额，单位分。 */
    private String transAmount;

    private String transType;

    /**
     * @return null 表示不是合法数字
     */
    public Long amountInFen() {
        if (transAmount == null || transAmount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(transAmount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    @Override
    public String toString() {
        return "RequestTicketRefundReqDTO{orderNo=" + orderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", transAmount=" + transAmount
                + ", transType=" + transType
                + ", deviceId=" + getDeviceId() + '}';
    }
}
