package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/** 旅游票退款子单明细。 */
public class DailyTicketRefundDetail {
    private String id;
    private String refundOrderNo;
    private String parentOrderNo;
    private String subOrderNo;
    private Integer refundAmount;
    private String refundStatus;
    private Date createTime;
    private Date updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getParentOrderNo() { return parentOrderNo; }
    public void setParentOrderNo(String parentOrderNo) { this.parentOrderNo = parentOrderNo; }
    public String getSubOrderNo() { return subOrderNo; }
    public void setSubOrderNo(String subOrderNo) { this.subOrderNo = subOrderNo; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
