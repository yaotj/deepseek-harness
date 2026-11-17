package com.chinasofti.huateng.model.collectpay;


public class AppPayOrderRegisterReqDTO {

    private String orderNo;

    private String userId;

    private String totalAmount;

    private String ticketType;

    private String cardId;

    private String msg;

    private String supplementFlag;

    private String deviceId;

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
