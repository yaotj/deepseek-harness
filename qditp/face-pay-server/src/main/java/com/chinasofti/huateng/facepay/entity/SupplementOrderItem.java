package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/** IF8A-26 补款单明细（SUPPLEMENT_ORDER_ITEM）：记录一张补款单覆盖的原过闸订单。 */
public class SupplementOrderItem {
    private Long id;
    private String orderNo;
    private String origOrderNo;
    private String origTxnDate;
    private Long origAmount;
    private String settleStatus;
    private String remark;
    private String activeOrigOrderNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getOrigOrderNo() { return origOrderNo; }
    public void setOrigOrderNo(String origOrderNo) { this.origOrderNo = origOrderNo; }

    public String getOrigTxnDate() { return origTxnDate; }
    public void setOrigTxnDate(String origTxnDate) { this.origTxnDate = origTxnDate; }

    public Long getOrigAmount() { return origAmount; }
    public void setOrigAmount(Long origAmount) { this.origAmount = origAmount; }

    public String getSettleStatus() { return settleStatus; }
    public void setSettleStatus(String settleStatus) { this.settleStatus = settleStatus; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getActiveOrigOrderNo() { return activeOrigOrderNo; }
    public void setActiveOrigOrderNo(String activeOrigOrderNo) { this.activeOrigOrderNo = activeOrigOrderNo; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "SupplementOrderItem{" +
                "orderNo='" + orderNo + '\'' +
                ", origOrderNo='" + origOrderNo + '\'' +
                ", settleStatus='" + settleStatus + '\'' +
                '}';
    }
}
