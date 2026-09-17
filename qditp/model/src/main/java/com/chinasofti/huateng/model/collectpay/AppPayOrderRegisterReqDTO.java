package com.chinasofti.huateng.model.collectpay;

/**
 * 把一张外部补款单登记成 {@code TBL_TVM_APP_ORDER} + {@code TBL_TVM_ORDER_PAY_PRE} 两行的请求。
 */
public class AppPayOrderRegisterReqDTO {

    /** 外部单号，同时作为两张表的 {@code ORDER_NO}，也是本接口的幂等键。 */
    private String orderNo;

    /** ITP 用户号，落 {@code USER_ID}，与 collect-pay 自己写的同命名空间。 */
    private String userId;

    /** 应收总额（单位：分）。 */
    private String totalAmount;

    /** 票种标记，落 {@code TICKET_TYPE}，用于人工在表里区分补款单与真实单程票。 */
    private String ticketType;

    /** 卡号，落 {@code RSV1}。 */
    private String cardId;

    /** 备注，落 {@code MSG}。 */
    private String msg;

    /** 补款标记，落 {@code RSV2} */
    private String supplementFlag;

    /** 来源标识，落前置单的 {@code DEVICE_ID}，便于在 collect-pay 侧一眼认出来源。 */
    private String deviceId;

    /** 前置单交易类型，补款单固定 {@code 03}（见类注释）。 */
    private String transType;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }

    public String getTotalAmount() { return totalAmount; }

    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }

    public String getTicketType() { return ticketType; }

    public void setTicketType(String ticketType) { this.ticketType = ticketType; }

    public String getCardId() { return cardId; }

    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getMsg() { return msg; }

    public void setMsg(String msg) { this.msg = msg; }

    public String getSupplementFlag() { return supplementFlag; }

    public void setSupplementFlag(String supplementFlag) { this.supplementFlag = supplementFlag; }

    public String getDeviceId() { return deviceId; }

    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getTransType() { return transType; }

    public void setTransType(String transType) { this.transType = transType; }

    @Override
    public String toString() {
        return "AppPayOrderRegisterReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", totalAmount='" + totalAmount + '\'' +
                ", ticketType='" + ticketType + '\'' +
                ", cardId='" + cardId + '\'' +
                ", supplementFlag='" + supplementFlag + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", transType='" + transType + '\'' +
                '}';
    }
}
