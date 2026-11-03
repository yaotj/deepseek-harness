package com.chinasofti.huateng.model.pay;

import java.io.Serializable;

/**
 * 超时罚金可退款订单异步导出 DTO。
 */
public class OvertimeRefundableExportDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String cardId;
    private String exitStationName;
    private String outStation;
    private Integer overtimeAmount;
    private Integer totalAmount;
    private String debitStatus;
    private String outTime;

    public String getOrderNo()
    {
        return orderNo;
    }

    public void setOrderNo(String orderNo)
    {
        this.orderNo = orderNo;
    }

    public String getCardId()
    {
        return cardId;
    }

    public void setCardId(String cardId)
    {
        this.cardId = cardId;
    }

    public String getExitStationName()
    {
        return exitStationName;
    }

    public void setExitStationName(String exitStationName)
    {
        this.exitStationName = exitStationName;
    }

    public String getOutStation()
    {
        return outStation;
    }

    public void setOutStation(String outStation)
    {
        this.outStation = outStation;
    }

    public Integer getOvertimeAmount()
    {
        return overtimeAmount;
    }

    public void setOvertimeAmount(Integer overtimeAmount)
    {
        this.overtimeAmount = overtimeAmount;
    }

    public Integer getTotalAmount()
    {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount)
    {
        this.totalAmount = totalAmount;
    }

    public String getDebitStatus()
    {
        return debitStatus;
    }

    public void setDebitStatus(String debitStatus)
    {
        this.debitStatus = debitStatus;
    }

    public String getOutTime()
    {
        return outTime;
    }

    public void setOutTime(String outTime)
    {
        this.outTime = outTime;
    }
}
