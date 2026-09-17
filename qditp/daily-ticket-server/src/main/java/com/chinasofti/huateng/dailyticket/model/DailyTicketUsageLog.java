package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/** 日票/计次票扣次使用明细。 */
public class DailyTicketUsageLog {
    private Long id;
    private String cardNum;
    private String orderNo;
    private String txnDate;
    private String inStation;
    private String outStation;
    private Integer timesBefore;
    private Integer timesAfter;
    private String ticketStatus;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public String getInStation() { return inStation; }
    public void setInStation(String inStation) { this.inStation = inStation; }

    public String getOutStation() { return outStation; }
    public void setOutStation(String outStation) { this.outStation = outStation; }

    public Integer getTimesBefore() { return timesBefore; }
    public void setTimesBefore(Integer timesBefore) { this.timesBefore = timesBefore; }

    public Integer getTimesAfter() { return timesAfter; }
    public void setTimesAfter(Integer timesAfter) { this.timesAfter = timesAfter; }

    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
