package com.chinasofti.huateng.model.dailyticket;

import java.io.Serializable;
import java.util.Date;

/**
 * 日票 / 旅游票可退款订单异步导出 DTO。
 */
public class DailyTicketRefundOrderExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String orderType;
    private String orderNo;
    private String paymentOrderNo;
    private Integer totalAmount;
    private String orderStatus;
    private String payStatus;
    private String ticketStatus;
    private String refundStatus;
    private Date createTime;
    private Date verifyAfterTime;

    public String getOrderType()
    {
        return orderType;
    }

    public void setOrderType(String orderType)
    {
        this.orderType = orderType;
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

    public Integer getTotalAmount()
    {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount)
    {
        this.totalAmount = totalAmount;
    }

    public String getOrderStatus()
    {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus)
    {
        this.orderStatus = orderStatus;
    }

    public String getPayStatus()
    {
        return payStatus;
    }

    public void setPayStatus(String payStatus)
    {
        this.payStatus = payStatus;
    }

    public String getTicketStatus()
    {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus)
    {
        this.ticketStatus = ticketStatus;
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
}
