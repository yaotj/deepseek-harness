package com.chinasofti.huateng.model.dailyticket;

import java.io.Serializable;
import java.util.Date;

/**
 * 日票退款记录异步导出 DTO。
 */
public class DailyTicketRefundRecordExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String refundOrderNo;
    private String platformRefundNo;
    private String orderNo;
    private String paymentOrderNo;
    private Integer refundAmount;
    private String refundType;
    private String refundStatus;
    private Date createTime;
    private Date verifyAfterTime;
    private Date refundDate;

    public String getRefundOrderNo()
    {
        return refundOrderNo;
    }

    public void setRefundOrderNo(String refundOrderNo)
    {
        this.refundOrderNo = refundOrderNo;
    }

    public String getPlatformRefundNo()
    {
        return platformRefundNo;
    }

    public void setPlatformRefundNo(String platformRefundNo)
    {
        this.platformRefundNo = platformRefundNo;
    }

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getPaymentOrderNo()
    {
        return paymentOrderNo;
    }

    public void setPaymentOrderNo(String paymentOrderNo)
    {
        this.paymentOrderNo = paymentOrderNo;
    }

    public Integer getRefundAmount()
    {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount)
    {
        this.refundAmount = refundAmount;
    }

    public String getRefundType()
    {
        return refundType;
    }

    public void setRefundType(String refundType)
    {
        this.refundType = refundType;
    }

    public String getRefundStatus()
    {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus)
    {
        this.refundStatus = refundStatus;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getVerifyAfterTime()
    {
        return verifyAfterTime;
    }

    public void setVerifyAfterTime(Date verifyAfterTime)
    {
        this.verifyAfterTime = verifyAfterTime;
    }

    public Date getRefundDate()
    {
        return refundDate;
    }

    public void setRefundDate(Date refundDate)
    {
        this.refundDate = refundDate;
    }
}
