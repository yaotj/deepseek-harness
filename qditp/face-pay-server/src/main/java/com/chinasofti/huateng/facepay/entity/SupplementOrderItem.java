package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * IF8A-26 补款单明细（SUPPLEMENT_ORDER_ITEM）：记录一张补款单覆盖的原过闸订单。
 *
 * <p>{@code settleStatus} 取值：{@code PENDING} 待结清 / {@code SETTLED} 原订单已收敛为 SUCCESS /
 * {@code FAILED} 原订单收敛失败需人工核对。这只是补款侧的对齐结果，
 * **NEVER** 用它替代 GATE_TXN_PAY.DEBIT_STATUS 判断一笔乘车订单是否已结清。</p>
 *
 * <p>{@code activeOrigOrderNo} 是「本明细当前是否仍在独占该原订单」的硬约束列，由唯一索引
 * {@code UK_SUPPLEMENT_ITEM_ACTIVE} 承载，落单时等于 {@code origOrderNo}。它**不是业务字段**，
 * 唯一用途是让「同一笔欠费被两张补款单同时覆盖」在 DB 层就 INSERT 失败。
 * 置 NULL 即释放独占（Oracle 唯一索引不约束全 NULL 行）。</p>
 */
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
