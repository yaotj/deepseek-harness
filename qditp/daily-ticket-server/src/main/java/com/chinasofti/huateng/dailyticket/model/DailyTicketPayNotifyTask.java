package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/** IF8B-05 支付结果通知 APP 的补偿任务。 */
public class DailyTicketPayNotifyTask {
    private String id;
    private String orderNo;
    private String orderType;
    private String payResult;
    private String payload;
    private String notifyStatus;
    private Integer notifyTimes;
    private Date notifyTime;
    private String notifyMsg;
    private Date createTime;
    private Date updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getPayResult() { return payResult; }
    public void setPayResult(String payResult) { this.payResult = payResult; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getNotifyStatus() { return notifyStatus; }
    public void setNotifyStatus(String notifyStatus) { this.notifyStatus = notifyStatus; }
    public Integer getNotifyTimes() { return notifyTimes; }
    public void setNotifyTimes(Integer notifyTimes) { this.notifyTimes = notifyTimes; }
    public Date getNotifyTime() { return notifyTime; }
    public void setNotifyTime(Date notifyTime) { this.notifyTime = notifyTime; }
    public String getNotifyMsg() { return notifyMsg; }
    public void setNotifyMsg(String notifyMsg) { this.notifyMsg = notifyMsg; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
