package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

public class DailyTicketRefund {
    private String id;
    private String orderNo;
    private String orderType;
    private String refundScope;
    private String parentOrderNo;
    private String refundReason;
    private String operator;
    private String refundOrderNo;
    private String platformRefundNo;
    private Integer refundAmount;
    private String refundStatus;
    private String refundType;
    private Date refundDate;
    private Date verifyAfterTime;
    private Date createTime;
    private Date updateTime;
    private String notifyStatus;
    private Integer notifyTimes;
    private Date notifyTime;
    private String notifyMsg;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getRefundScope() { return refundScope; }
    public void setRefundScope(String refundScope) { this.refundScope = refundScope; }
    public String getParentOrderNo() { return parentOrderNo; }
    public void setParentOrderNo(String parentOrderNo) { this.parentOrderNo = parentOrderNo; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getPlatformRefundNo() { return platformRefundNo; }
    public void setPlatformRefundNo(String platformRefundNo) { this.platformRefundNo = platformRefundNo; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public String getRefundType() { return refundType; }
    public void setRefundType(String refundType) { this.refundType = refundType; }
    public Date getRefundDate() { return refundDate; }
    public void setRefundDate(Date refundDate) { this.refundDate = refundDate; }
    public Date getVerifyAfterTime() { return verifyAfterTime; }
    public void setVerifyAfterTime(Date verifyAfterTime) { this.verifyAfterTime = verifyAfterTime; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
    public String getNotifyStatus() { return notifyStatus; }
    public void setNotifyStatus(String notifyStatus) { this.notifyStatus = notifyStatus; }
    public Integer getNotifyTimes() { return notifyTimes; }
    public void setNotifyTimes(Integer notifyTimes) { this.notifyTimes = notifyTimes; }
    public Date getNotifyTime() { return notifyTime; }
    public void setNotifyTime(Date notifyTime) { this.notifyTime = notifyTime; }
    public String getNotifyMsg() { return notifyMsg; }
    public void setNotifyMsg(String notifyMsg) { this.notifyMsg = notifyMsg; }
}
