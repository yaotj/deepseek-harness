package com.chinasofti.huateng.alipay.paysign.entity;

import java.time.LocalDateTime;

/** 支付宝出行支付结果回调凭据。 */
public class AlipayPayCallbackLog {
    private String callbackSeq;
    private String orderNo;
    private String callbackType;
    private String transStatus;
    private String payStatus;
    private String channelVoucherId;
    private String transAmount;
    private String transTime;
    private String cardNo;
    private String rawBody;
    private String handleStatus;
    private String handleMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getCallbackSeq() {
        return callbackSeq;
    }

    public void setCallbackSeq(String callbackSeq) {
        this.callbackSeq = callbackSeq;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCallbackType() {
        return callbackType;
    }

    public void setCallbackType(String callbackType) {
        this.callbackType = callbackType;
    }

    public String getTransStatus() {
        return transStatus;
    }

    public void setTransStatus(String transStatus) {
        this.transStatus = transStatus;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getChannelVoucherId() {
        return channelVoucherId;
    }

    public void setChannelVoucherId(String channelVoucherId) {
        this.channelVoucherId = channelVoucherId;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public String getHandleStatus() {
        return handleStatus;
    }

    public void setHandleStatus(String handleStatus) {
        this.handleStatus = handleStatus;
    }

    public String getHandleMsg() {
        return handleMsg;
    }

    public void setHandleMsg(String handleMsg) {
        this.handleMsg = handleMsg;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
