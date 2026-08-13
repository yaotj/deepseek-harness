package com.chinasofti.huateng.para.entity.ticket;

import java.time.LocalDateTime;

/** 票卡类型对应的订单自动退款周期参数，周期单位为天。 */
public class OrderRefundCycle {
    /** 唯一票卡类型，作为该参数的业务主键。 */
    private String ticketType;

    /** 自动退款等待周期，单位天。 */
    private Integer autoRefundPeriod;

    /** 配置创建时间，由数据库维护。 */
    private LocalDateTime createTime;

    /** 配置最后修改时间，由数据库维护。 */
    private LocalDateTime updateTime;

    /** 配置适用说明或特殊处理备注。 */
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
