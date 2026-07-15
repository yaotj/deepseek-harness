package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/**
 * 日票退款记录。
 */
public class DailyTicketRefund {
    /** 主键ID。 */
    private String id;
    /** 原日票订单号。 */
    private String orderNo;
    /** 退款订单号。 */
    private String refundOrderNo;
    /** 退款金额，单位分。 */
    private Integer refundAmount;
    /** 退款状态。 */
    private String refundStatus;
    /** 退款类型，00直接退款，01激活后核验退款。 */
    private String refundType;
    /** 退款完成时间。 */
    private Date refundDate;
    /** 激活后退款核验时间。 */
    private Date verifyAfterTime;
    /** 创建时间。 */
    private Date createTime;
    /** 更新时间。 */
    private Date updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
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
}
