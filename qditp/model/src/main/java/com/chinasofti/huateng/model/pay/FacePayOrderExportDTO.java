package com.chinasofti.huateng.model.pay;

import java.io.Serializable;

/**
 * 当面付订单异步导出 DTO。
 */
public class FacePayOrderExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String orderStatus;
    private String channel;
    private String bizType;
    private String payChannelCode;
    private String payCenterOrderNo;
    private String payCenterChannelOrderNo;
    private String inStationName;
    private String outStationName;
    private Long ticketPrice;
    private Integer ticketNum;
    private Long orderAmount;
    private String singleTicketType;
    private String deviceId;
    private String refundStatus;
    private Long refundAmount;
    private String lastRefundTime;
    private String createTime;

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getOrderStatus()
    {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus)
    {
        this.orderStatus = orderStatus;
    }

    public String getChannel()
    {
        return channel;
    }

    public void setChannel(String channel)
    {
        this.channel = channel;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getPayChannelCode()
    {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode)
    {
        this.payChannelCode = payChannelCode;
    }

    public String getPayCenterOrderNo()
    {
        return payCenterOrderNo;
    }

    public void setPayCenterOrderNo(String payCenterOrderNo)
    {
        this.payCenterOrderNo = payCenterOrderNo;
    }

    public String getPayCenterChannelOrderNo()
    {
        return payCenterChannelOrderNo;
    }

    public void setPayCenterChannelOrderNo(String payCenterChannelOrderNo)
    {
        this.payCenterChannelOrderNo = payCenterChannelOrderNo;
    }

    public String getInStationName()
    {
        return inStationName;
    }

    public void setInStationName(String inStationName)
    {
        this.inStationName = inStationName;
    }

    public String getOutStationName()
    {
        return outStationName;
    }

    public void setOutStationName(String outStationName)
    {
        this.outStationName = outStationName;
    }

    public Long getTicketPrice()
    {
        return ticketPrice;
    }

    public void setTicketPrice(Long ticketPrice)
    {
        this.ticketPrice = ticketPrice;
    }

    public Integer getTicketNum()
    {
        return ticketNum;
    }

    public void setTicketNum(Integer ticketNum)
    {
        this.ticketNum = ticketNum;
    }

    public Long getOrderAmount()
    {
        return orderAmount;
    }

    public void setOrderAmount(Long orderAmount)
    {
        this.orderAmount = orderAmount;
    }

    public String getSingleTicketType()
    {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType)
    {
        this.singleTicketType = singleTicketType;
    }

    public String getDeviceId()
    {
        return deviceId;
    }

    public void setDeviceId(String deviceId)
    {
        this.deviceId = deviceId;
    }

    public String getRefundStatus()
    {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus)
    {
        this.refundStatus = refundStatus;
    }

    public Long getRefundAmount()
    {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount)
    {
        this.refundAmount = refundAmount;
    }

    public String getLastRefundTime()
    {
        return lastRefundTime;
    }

    public void setLastRefundTime(String lastRefundTime)
    {
        this.lastRefundTime = lastRefundTime;
    }

    public String getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(String createTime)
    {
        this.createTime = createTime;
    }
}
