package com.chinasofti.huateng.para.entity.ticket;

import java.time.LocalDateTime;

public class OrderRefundCycle {
    private String ticketType;

    private Integer autoRefundPeriod;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;

    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }
    public Integer getAutoRefundPeriod() { return autoRefundPeriod; }
    public void setAutoRefundPeriod(Integer autoRefundPeriod) { this.autoRefundPeriod = autoRefundPeriod; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
