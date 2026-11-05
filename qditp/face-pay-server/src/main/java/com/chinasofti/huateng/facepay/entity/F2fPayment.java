package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

public class F2fPayment {

    private Long id;

    private String orderNo;

    private Integer attemptNo;

    private String payScene;

    private String payStatus;

    private Long payAmount;

    private Integer cashAmount;

    private Integer couponAmount;

    private String payType;

    private String authCode;

    private String paymentVendor;

    private String payChannelCode;

    private String payUrl;

    private String payCenterOrderNo;

    private String channelOrderNo;

    private String requestBody;

    private String responseBody;

    private String retCode;

    private String retMsg;

    private Integer costMs;

    private LocalDateTime requestTms;

    private LocalDateTime finishTms;

    private LocalDateTime createTms;

    private LocalDateTime updateTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public String getPayScene() {
        return payScene;
    }

    public void setPayScene(String payScene) {
        this.payScene = payScene;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public Long getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Long payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(Integer cashAmount) {
        this.cashAmount = cashAmount;
    }

    public Integer getCouponAmount() {
        return couponAmount;
    }

    public void setCouponAmount(Integer couponAmount) {
        this.couponAmount = couponAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }
    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getPayCenterOrderNo() {
        return payCenterOrderNo;
    }

    public void setPayCenterOrderNo(String payCenterOrderNo) {
        this.payCenterOrderNo = payCenterOrderNo;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

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

    public Integer getCostMs() {
        return costMs;
    }

    public void setCostMs(Integer costMs) {
        this.costMs = costMs;
    }

    public LocalDateTime getRequestTms() {
        return requestTms;
    }

    public void setRequestTms(LocalDateTime requestTms) {
        this.requestTms = requestTms;
    }

    public LocalDateTime getFinishTms() {
        return finishTms;
    }

    public void setFinishTms(LocalDateTime finishTms) {
        this.finishTms = finishTms;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
