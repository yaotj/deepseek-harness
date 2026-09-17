package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** IF2A-09 票卡充值下单请求报文。 */
public class RequestTopupReqDTO extends BaseDeviceRequest {

    /** 票卡逻辑卡号。 */
    private String ticketLogicNum;

    /** 票卡物理卡号。 */
    private String ticketPhysicsNum;

    /** 充值前卡内余额，单位分。 */
    private String beforeAmount;

    /** 本次充值金额，单位分。 */
    private String transAmount;

    /** 支付方式：{@code 0} 聚合码，其余走支付中心。 */
    private String payType;

    /** 充值金额转 {@code Long}（分）；为空或非数字返回 null。 */
    public Long transAmountInFen() {
        return parse(transAmount);
    }

    /** 充值前余额转 {@code Long}（分）；为空、非数字或负数返回 null。 */
    public Long beforeAmountInFen() {
        Long parsed = parse(beforeAmount);
        return parsed != null && parsed >= 0 ? parsed : null;
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public String getBeforeAmount() {
        return beforeAmount;
    }

    public void setBeforeAmount(String beforeAmount) {
        this.beforeAmount = beforeAmount;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestTopupReqDTO{ticketLogicNum=" + ticketLogicNum
                + ", ticketPhysicsNum=" + ticketPhysicsNum
                + ", beforeAmount=" + beforeAmount
                + ", transAmount=" + transAmount
                + ", payType=" + payType + '}';
    }
}
